package com.github.appachan.rssreader.feature.feed.reducer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pattern ③ ViewModel for the feed list (State reducer / 素の ViewModel). Same screen and same
 * requirements as pattern ① — dependency-ordered parallel fetch, pull-to-refresh that keeps
 * existing rows, error handling — but the state never changes outside [reduce]. The ViewModel's
 * only job is to run the side effects (observe the SSOT, fetch articles, call the repository) and
 * translate each outcome into a [FeedAction]; what that does to the state is decided by the pure
 * reducer.
 *
 * One-shot events (a refresh/delete failure shown as a transient message) are not state, so they
 * are emitted on [events] rather than reduced.
 */
class ReducerFeedListViewModel(
    private val feedRepository: FeedRepository,
    private val articleRepository: ArticleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReducerFeedState())
    val state: StateFlow<ReducerFeedState> = _state.asStateFlow()

    // One-shot events. extraBufferCapacity = 1 so emit never suspends when the screen is not
    // currently collecting.
    private val _events = MutableSharedFlow<ReducerFeedEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReducerFeedEvent> = _events.asSharedFlow()

    private var loaded = false
    private var refreshJob: Job? = null
    private var articleJobs: List<Job> = emptyList()

    /** The single funnel: every state change flows through the pure [reduce] here. */
    private fun dispatch(action: FeedAction) {
        _state.update { reduce(it, action) }
    }

    /**
     * Initial load: start observing the SSOT and trigger the first network refresh. Idempotent and
     * driven from the screen's `LaunchedEffect`, so it runs once per ViewModel even across
     * recompositions and configuration changes (no constructor side effects → trivially testable).
     */
    fun load() {
        if (loaded) return
        loaded = true
        observeFeeds()
        triggerRefresh(initial = true)
    }

    /** Pull-to-refresh: keep showing existing rows while refreshing. */
    fun refresh() {
        triggerRefresh(initial = false)
    }

    /** Re-attempt after an error. Treated as an initial load when nothing is shown yet. */
    fun retry() {
        triggerRefresh(initial = _state.value.items.isEmpty())
    }

    // --- Add feed ---

    fun showAddSheet() {
        dispatch(FeedAction.AddFeed.SheetOpened)
    }

    fun dismissAddSheet() {
        dispatch(FeedAction.AddFeed.SheetDismissed)
    }

    fun onAddUrlChange(url: String) {
        dispatch(FeedAction.AddFeed.UrlChanged(url))
    }

    /**
     * Validate the URL locally, then delegate persistence/validation to [FeedRepository.addFeed].
     * Success closes the dialog (the new feed appears via [observeFeeds]); failure keeps the dialog
     * open with an error. Ignores re-entrancy while a request is in flight.
     */
    fun submitAddFeed() {
        if (_state.value.isAddingFeed) return
        val url = _state.value.addUrlInput.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            dispatch(FeedAction.AddFeed.Failed(AddFeedError.INVALID_URL))
            return
        }
        // Mark in-flight synchronously so a second tap before the coroutine runs is ignored.
        dispatch(FeedAction.AddFeed.Started)
        viewModelScope.launch {
            feedRepository
                .addFeed(url)
                .fold(
                    onSuccess = {
                        dispatch(FeedAction.AddFeed.Succeeded)
                        // addFeed only persists feed metadata; pull the new feed's articles so its
                        // row resolves instead of spinning on "loading" forever.
                        triggerRefresh(initial = false)
                    },
                    onFailure = { dispatch(FeedAction.AddFeed.Failed(AddFeedError.COULD_NOT_ADD)) },
                )
        }
    }

    // --- Delete feed ---

    fun requestDeleteFeed(id: FeedId) {
        dispatch(FeedAction.Delete.Requested(id))
    }

    fun cancelDeleteFeed() {
        dispatch(FeedAction.Delete.Cancelled)
    }

    /**
     * Confirm deletion of [ReducerFeedState.pendingDeletion]; the row disappears via
     * [observeFeeds].
     */
    fun confirmDeleteFeed() {
        val id = _state.value.pendingDeletion ?: return
        dispatch(FeedAction.Delete.Confirmed)
        viewModelScope.launch {
            feedRepository.removeFeed(id).onFailure { _events.emit(ReducerFeedEvent.DeleteFailed) }
        }
    }

    private fun observeFeeds(): Job = viewModelScope.launch {
        feedRepository.observeFeeds().collect { feeds ->
            dispatch(FeedAction.FeedsObserved(feeds))
            restartArticleJobs(feeds)
        }
    }

    private fun restartArticleJobs(feeds: List<Feed>) {
        articleJobs.forEach { it.cancel() }
        articleJobs = feeds.map { feed -> launchLatestArticle(feed) }
    }

    private fun launchLatestArticle(feed: Feed): Job = viewModelScope.launch {
        articleRepository.observeArticles(feed.id).collect { articles ->
            // Empty means "not fetched yet" (the SSOT cache starts empty); keep the row loading
            // until articles actually arrive. A genuinely empty feed is resolved when the
            // surrounding refresh completes (RefreshSucceeded clears every row's spinner).
            if (articles.isEmpty()) return@collect
            // Latest = newest publishedAt; undated articles sort oldest and ties break by id, so
            // the
            // choice is deterministic (kotlinx Instant? compares null as the smallest value).
            val latest =
                articles.maxWithOrNull(compareBy({ it.publishedAt }, { it.id.value }))
                    ?: return@collect
            dispatch(FeedAction.ArticleResolved(feed.id, latest))
        }
    }

    private fun triggerRefresh(initial: Boolean) {
        // Serialize refreshes: ignore a new request while one is still in flight so overlapping
        // refresh()/retry() calls can't interleave their flag writes.
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            dispatch(if (initial) FeedAction.LoadStarted else FeedAction.RefreshStarted)
            feedRepository
                .refreshAllFeeds()
                .fold(
                    onSuccess = { dispatch(FeedAction.RefreshSucceeded) },
                    onFailure = {
                        // Surface the failure full-screen only when nothing is shown yet (the
                        // reducer decides that from the current items), otherwise as a one-shot
                        // snackbar event.
                        val hadRows = _state.value.items.isNotEmpty()
                        dispatch(FeedAction.RefreshFailed)
                        if (hadRows) _events.emit(ReducerFeedEvent.RefreshFailed)
                    },
                )
        }
    }
}
