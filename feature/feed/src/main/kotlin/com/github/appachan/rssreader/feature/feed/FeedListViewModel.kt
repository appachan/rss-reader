package com.github.appachan.rssreader.feature.feed

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pattern ① ViewModel for the feed list. State management is intentionally the naive flat approach:
 * a single [MutableStateFlow] of [FeedListUiState] mutated imperatively, no Async wrapper and no
 * `combine`-as-architecture (those are TASK-011〜014).
 *
 * Dependency-ordered parallel fetch: the feed list is observed first; for each feed an independent
 * coroutine resolves its latest article and updates only that row, so rows fill in as their data
 * arrives. Pull-to-refresh keeps the existing rows visible while [refreshAllFeeds] re-populates the
 * single source of truth.
 */
class FeedListViewModel(
    private val feedRepository: FeedRepository,
    private val articleRepository: ArticleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedListUiState())
    val uiState: StateFlow<FeedListUiState> = _uiState.asStateFlow()

    // One-shot events (a refresh failure shown as a transient message). extraBufferCapacity = 1 so
    // emit never suspends when the screen is not currently collecting.
    private val _events = MutableSharedFlow<FeedListEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FeedListEvent> = _events.asSharedFlow()

    private var loaded = false
    private var refreshJob: Job? = null
    private var articleJobs: List<Job> = emptyList()

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

    /**
     * Pull-to-refresh: keep showing existing rows while
     * [isRefreshing][FeedListUiState.isRefreshing].
     */
    fun refresh() {
        triggerRefresh(initial = false)
    }

    /** Re-attempt after an error. Treated as an initial load when nothing is shown yet. */
    fun retry() {
        triggerRefresh(initial = _uiState.value.items.isEmpty())
    }

    // --- Add feed ---

    fun showAddSheet() {
        _uiState.update { it.copy(isAddSheetVisible = true, addUrlInput = "", addFeedError = null) }
    }

    fun dismissAddSheet() {
        _uiState.update {
            it.copy(
                isAddSheetVisible = false,
                addUrlInput = "",
                addFeedError = null,
                isAddingFeed = false,
            )
        }
    }

    fun onAddUrlChange(url: String) {
        // Clear the error as soon as the user edits, so it isn't shown against new input.
        _uiState.update { it.copy(addUrlInput = url, addFeedError = null) }
    }

    /**
     * Validate the URL locally, then delegate persistence/validation to [FeedRepository.addFeed].
     * Success closes the dialog (the new feed appears via [observeFeeds]); failure keeps the dialog
     * open with an error. Ignores re-entrancy while a request is in flight.
     */
    fun submitAddFeed() {
        if (_uiState.value.isAddingFeed) return
        val url = _uiState.value.addUrlInput.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            _uiState.update { it.copy(addFeedError = AddFeedError.INVALID_URL) }
            return
        }
        // Mark in-flight synchronously so a second tap before the coroutine runs is ignored.
        _uiState.update { it.copy(isAddingFeed = true, addFeedError = null) }
        viewModelScope.launch {
            feedRepository
                .addFeed(url)
                .fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isAddingFeed = false,
                                isAddSheetVisible = false,
                                addUrlInput = "",
                                addFeedError = null,
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update {
                            it.copy(isAddingFeed = false, addFeedError = AddFeedError.COULD_NOT_ADD)
                        }
                    },
                )
        }
    }

    // --- Delete feed ---

    fun requestDeleteFeed(id: FeedId) {
        _uiState.update { it.copy(pendingDeletion = id) }
    }

    fun cancelDeleteFeed() {
        _uiState.update { it.copy(pendingDeletion = null) }
    }

    /**
     * Confirm deletion of [FeedListUiState.pendingDeletion]; the row disappears via [observeFeeds].
     */
    fun confirmDeleteFeed() {
        val id = _uiState.value.pendingDeletion ?: return
        _uiState.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch {
            feedRepository.removeFeed(id).onFailure { _events.emit(FeedListEvent.DeleteFailed) }
        }
    }

    private fun observeFeeds(): Job = viewModelScope.launch {
        feedRepository.observeFeeds().collect { feeds ->
            _uiState.update { state -> state.copy(items = reconcileRows(state.items, feeds)) }
            restartArticleJobs(feeds)
        }
    }

    private fun reconcileRows(
        current: List<FeedRowUiModel>,
        feeds: List<Feed>,
    ): List<FeedRowUiModel> {
        val existing = current.associateBy { it.feedId }
        return feeds.map { feed ->
            // Preserve an already-resolved article so a refresh doesn't flip the row back to
            // loading.
            existing[feed.id]?.copy(title = feed.title)
                ?: FeedRowUiModel(
                    feedId = feed.id,
                    title = feed.title,
                    latestArticleTitle = null,
                    latestArticlePublishedAt = null,
                    isArticleLoading = true,
                )
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
            // surrounding refresh completes (see triggerRefresh).
            if (articles.isEmpty()) return@collect
            // Latest = newest publishedAt; undated articles sort oldest and ties break by id, so
            // the
            // choice is deterministic (kotlinx Instant? compares null as the smallest value).
            val latest = articles.maxWithOrNull(compareBy({ it.publishedAt }, { it.id.value }))
            _uiState.update { state ->
                state.copy(
                    items =
                        state.items.map { row ->
                            if (row.feedId == feed.id) {
                                row.copy(
                                    latestArticleTitle = latest?.title,
                                    latestArticlePublishedAt = latest?.publishedAt,
                                    isArticleLoading = false,
                                )
                            } else {
                                row
                            }
                        }
                )
            }
        }
    }

    private fun triggerRefresh(initial: Boolean) {
        // Serialize refreshes: ignore a new request while one is still in flight so overlapping
        // refresh()/retry() calls can't interleave their flag writes.
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { state ->
                if (initial) {
                    state.copy(isLoading = true, isError = false)
                } else {
                    state.copy(isRefreshing = true, isError = false)
                }
            }
            feedRepository
                .refreshAllFeeds()
                .fold(
                    onSuccess = {
                        // Refresh persisted everything (see FeedRepository.refreshAllFeeds): any
                        // row still loading simply has no articles.
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                isRefreshing = false,
                                items = state.items.map { it.copy(isArticleLoading = false) },
                            )
                        }
                    },
                    onFailure = {
                        // Keep existing rows visible: surface the failure full-screen only when
                        // nothing is shown yet, otherwise as a one-shot snackbar event.
                        val hadRows = _uiState.value.items.isNotEmpty()
                        _uiState.update { state ->
                            state.copy(isLoading = false, isRefreshing = false, isError = !hadRows)
                        }
                        if (hadRows) _events.emit(FeedListEvent.RefreshFailed)
                    },
                )
        }
    }
}
