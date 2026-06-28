package com.github.appachan.rssreader.feature.feed.async

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pattern ② ViewModel (Async 型 + combine). Same screen and requirements as pattern ①, but the data
 * state is assembled declaratively instead of mutated imperatively:
 *
 * - [feedRows] `combine`s three sources — the feed list (SSOT), the per-feed latest articles, and
 *   the fetch status [fetchStatus] — into a single `Async<List<FeedRowUiModel>>`. This is the
 *   pattern's strength: multiple sources merge cleanly and each row fills in independently
 *   (依存関係あり並列取得 / 取得できた行から順次表示).
 * - [localState] holds the imperative bits (pull-to-refresh flag, dialog state) and is `combine`d
 *   on top, so the public [uiState] is a pure function of its inputs.
 *
 * `Refreshing` can't live in [Async] without doubling up `data`, so it stays a flat Boolean — the
 * documented 妥協点 of this pattern.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AsyncFeedListViewModel(
    private val feedRepository: FeedRepository,
    private val articleRepository: ArticleRepository,
) : ViewModel() {

    /**
     * Status of the in-flight fetch (initial load / retry / refresh). Drives whether an empty feed
     * list reads as Loading, Failure, or genuinely-empty Success, and lets a per-feed row with no
     * articles resolve to "no articles" once the fetch has finished.
     */
    private val fetchStatus = MutableStateFlow<Async<Unit>>(Async.Idle)

    /**
     * Imperative, source-less UI bits combined on top of the derived data. The [feeds] field is
     * unused here (it comes from [feedRows]); only the flat fields are read.
     */
    private val localState = MutableStateFlow(AsyncFeedListUiState())

    /**
     * The feed list (SSOT) shared as a single StateFlow so the two `combine`s below — and [hasRows]
     * — subscribe to the repository once instead of opening a collector each.
     */
    private val feeds =
        feedRepository.observeFeeds().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val latestArticles = feeds.flatMapLatest { feeds ->
        if (feeds.isEmpty()) {
            flowOf(emptyMap())
        } else {
            // One Async<LatestArticle?> per feed, merged into a map keyed by FeedId. combine
            // re-emits whenever any single feed's articles (or the fetch status) change, so
            // rows
            // resolve independently as their data arrives.
            combine(
                feeds.map { feed ->
                    combine(articleRepository.observeArticles(feed.id), fetchStatus) {
                        articles,
                        status ->
                        feed.id to latestArticleAsync(articles, status)
                    }
                }
            ) { entries ->
                entries.toMap()
            }
        }
    }

    private val feedRows =
        combine(feeds, latestArticles, fetchStatus) { feeds, articles, status ->
            when {
                // Once feeds are loaded, keep showing them through refreshes and failures; per-row
                // article state carries its own progress.
                feeds.isNotEmpty() ->
                    Async.Success(
                        feeds.map { feed ->
                            FeedRowUiModel(
                                feedId = feed.id,
                                title = feed.title,
                                latestArticle = articles[feed.id] ?: Async.Loading,
                            )
                        }
                    )
                status is Async.Loading -> Async.Loading
                status is Async.Failure -> Async.Failure(status.error)
                status is Async.Success -> Async.Success(emptyList())
                else -> Async.Idle
            }
        }

    val uiState: StateFlow<AsyncFeedListUiState> =
        combine(feedRows, localState) { feeds, local -> local.copy(feeds = feeds) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AsyncFeedListUiState())

    // One-shot events (a refresh/delete failure shown as a transient message). extraBufferCapacity
    // = 1 so emit never suspends when the screen is not currently collecting.
    private val _events = MutableSharedFlow<FeedListEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FeedListEvent> = _events.asSharedFlow()

    private var loaded = false
    private var refreshJob: Job? = null

    /**
     * Initial load: trigger the first network fetch. The feed list and articles are already being
     * observed by [uiState]'s `combine`, so there is nothing else to start. Idempotent and driven
     * from the screen's `LaunchedEffect`, so it runs once per ViewModel across recompositions and
     * configuration changes.
     */
    fun load() {
        if (loaded) return
        loaded = true
        triggerFetch(initial = true)
    }

    /** Pull-to-refresh: keep showing existing rows while [AsyncFeedListUiState.isRefreshing]. */
    fun refresh() {
        triggerFetch(initial = false)
    }

    /** Re-attempt after an error. Treated as an initial load when nothing is shown yet. */
    fun retry() {
        triggerFetch(initial = !hasRows())
    }

    // --- Add feed ---

    fun showAddSheet() {
        localState.update {
            it.copy(isAddSheetVisible = true, addUrlInput = "", addFeedError = null)
        }
    }

    fun dismissAddSheet() {
        localState.update {
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
        localState.update { it.copy(addUrlInput = url, addFeedError = null) }
    }

    /**
     * Validate the URL locally, then delegate persistence/validation to [FeedRepository.addFeed].
     * Success closes the dialog (the new feed appears via the observed feed list); failure keeps
     * the dialog open with an error. Ignores re-entrancy while a request is in flight.
     */
    fun submitAddFeed() {
        if (localState.value.isAddingFeed) return
        val url = localState.value.addUrlInput.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            localState.update { it.copy(addFeedError = AddFeedError.INVALID_URL) }
            return
        }
        // Mark in-flight synchronously so a second tap before the coroutine runs is ignored.
        localState.update { it.copy(isAddingFeed = true, addFeedError = null) }
        viewModelScope.launch {
            feedRepository
                .addFeed(url)
                .fold(
                    onSuccess = {
                        localState.update {
                            it.copy(
                                isAddingFeed = false,
                                isAddSheetVisible = false,
                                addUrlInput = "",
                                addFeedError = null,
                            )
                        }
                        // addFeed only persists feed metadata; pull the new feed's articles so its
                        // row resolves instead of spinning on "loading" forever.
                        triggerFetch(initial = false)
                    },
                    onFailure = {
                        localState.update {
                            it.copy(isAddingFeed = false, addFeedError = AddFeedError.COULD_NOT_ADD)
                        }
                    },
                )
        }
    }

    // --- Delete feed ---

    fun requestDeleteFeed(id: FeedId) {
        localState.update { it.copy(pendingDeletion = id) }
    }

    fun cancelDeleteFeed() {
        localState.update { it.copy(pendingDeletion = null) }
    }

    /**
     * Confirm deletion of [AsyncFeedListUiState.pendingDeletion]; the row disappears via the
     * observed feed list.
     */
    fun confirmDeleteFeed() {
        val id = localState.value.pendingDeletion ?: return
        localState.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch {
            feedRepository.removeFeed(id).onFailure { _events.emit(FeedListEvent.DeleteFailed) }
        }
    }

    private fun triggerFetch(initial: Boolean) {
        // Serialize fetches: ignore a new request while one is still in flight so overlapping
        // refresh()/retry() calls can't interleave their status writes.
        if (refreshJob?.isActive == true) return
        // Loading clears any prior Failure and re-shows per-row article spinners; the existing list
        // stays visible because feedRows keeps a non-empty feed list as Success.
        fetchStatus.value = Async.Loading
        if (!initial) localState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            feedRepository
                .refreshAllFeeds()
                .fold(
                    onSuccess = { fetchStatus.value = Async.Success(Unit) },
                    onFailure = { error ->
                        // Keep existing rows visible: surface the failure full-screen only when
                        // nothing is shown yet, otherwise as a one-shot snackbar event.
                        val hadRows = hasRows()
                        fetchStatus.value = Async.Failure(error)
                        if (hadRows) _events.emit(FeedListEvent.RefreshFailed)
                    },
                )
            localState.update { it.copy(isRefreshing = false) }
        }
    }

    // Read the SSOT directly rather than the derived uiState: feedRows renders a non-empty feed
    // list as Success, so "feeds present" is exactly "rows on screen".
    private fun hasRows(): Boolean = feeds.value.isNotEmpty()

    private fun latestArticleAsync(
        articles: List<Article>,
        status: Async<Unit>,
    ): Async<LatestArticle?> =
        when {
            // Latest = newest publishedAt; undated articles sort oldest and ties break by id, so
            // the
            // choice is deterministic (kotlinx Instant? compares null as the smallest value).
            articles.isNotEmpty() -> {
                val latest = articles.maxWith(compareBy({ it.publishedAt }, { it.id.value }))
                Async.Success(LatestArticle(latest.title, latest.publishedAt))
            }
            // Empty means "not fetched yet" until the surrounding fetch finishes; only then is the
            // feed genuinely article-less.
            status is Async.Success -> Async.Success(null)
            else -> Async.Loading
        }
}
