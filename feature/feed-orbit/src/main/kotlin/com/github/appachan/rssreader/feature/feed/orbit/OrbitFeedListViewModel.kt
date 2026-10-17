package com.github.appachan.rssreader.feature.feed.orbit

import androidx.lifecycle.ViewModel
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.container

/**
 * Pattern ④ container host for the feed list (Orbit MVI). Same screen, same requirements and same
 * acceptance conditions as pattern ①
 * ([com.github.appachan.rssreader.feature.feed.FeedListViewModel]); only the state-management
 * technique changes:
 *
 * - The single source of truth is the Orbit [Container]; every state change goes through `reduce`
 *   (a pure `state -> state` step) instead of an imperative `MutableStateFlow.update`. Reduces are
 *   serialized by the container, so concurrent intents can't interleave partial writes.
 * - One-shot events use Orbit's built-in side-effect channel ([FeedListSideEffect]) rather than a
 *   hand-rolled `MutableSharedFlow`.
 * - Initial work runs in the container's `onCreate` lambda — Orbit's idiomatic "load once" hook, so
 *   the screen no longer needs a `LaunchedEffect { load() }` (it runs when the state is first
 *   collected, and `runOnCreate()` drives it in tests).
 *
 * Dependency-ordered parallel fetch is preserved: the feed list is observed first, and each feed's
 * latest article resolves in its own child coroutine so rows fill in independently. Pull-to-refresh
 * keeps existing rows visible while [triggerRefresh] re-populates the source of truth.
 */
class OrbitFeedListViewModel(
    private val feedRepository: FeedRepository,
    private val articleRepository: ArticleRepository,
) : ContainerHost<OrbitFeedListState, FeedListSideEffect>, ViewModel() {

    override val container: Container<OrbitFeedListState, FeedListSideEffect> =
        container(OrbitFeedListState()) {
            // onCreate: start observing the SSOT and trigger the first network refresh. Both run
            // for the container's lifetime; the feed observer never returns (it collects a hot
            // flow), which is the documented Orbit pattern for long-running collectors.
            coroutineScope {
                launch { observeFeeds() }
                launch { triggerRefresh(initial = true) }
            }
        }

    // Serialize refreshes: a flag flipped at the start of an in-flight refresh so an overlapping
    // refresh()/retry() is ignored (the equivalent of pattern ①'s `refreshJob?.isActive` guard).
    private var refreshing = false

    // De-dupe add-feed submits synchronously at dispatch time: unlike pattern ①'s synchronous
    // state write, an Orbit intent's `reduce` runs later on the event loop, so a state flag can't
    // stop a second tap that is enqueued before the first intent executes. This plain flag closes
    // that window while the URL itself is still read inside the intent (after onAddUrlChange).
    private var submitting = false

    /** Pull-to-refresh: keep showing existing rows while [OrbitFeedListState.isRefreshing]. */
    fun refresh() {
        intent { triggerRefresh(initial = false) }
    }

    /** Re-attempt after an error. Treated as an initial load when nothing is shown yet. */
    fun retry() {
        intent { triggerRefresh(initial = state.items.isEmpty()) }
    }

    // --- Add feed ---

    fun showAddSheet() {
        intent {
            reduce { state.copy(isAddSheetVisible = true, addUrlInput = "", addFeedError = null) }
        }
    }

    fun dismissAddSheet() {
        intent {
            reduce {
                state.copy(
                    isAddSheetVisible = false,
                    addUrlInput = "",
                    addFeedError = null,
                    isAddingFeed = false,
                )
            }
        }
    }

    fun onAddUrlChange(url: String) {
        // Clear the error as soon as the user edits, so it isn't shown against new input.
        intent { reduce { state.copy(addUrlInput = url, addFeedError = null) } }
    }

    /**
     * Validate the URL locally, then delegate persistence/validation to [FeedRepository.addFeed].
     * Success closes the dialog (the new feed appears via the feed observer); failure keeps the
     * dialog open with an error. Re-entrancy while a request is in flight is ignored.
     */
    fun submitAddFeed() {
        if (submitting) return
        submitting = true
        intent {
            val url = state.addUrlInput.trim()
            if (!url.startsWith("https://", ignoreCase = true)) {
                reduce { state.copy(addFeedError = AddFeedError.INVALID_URL) }
                submitting = false
                return@intent
            }
            reduce { state.copy(isAddingFeed = true, addFeedError = null) }
            feedRepository
                .addFeed(url)
                .fold(
                    onSuccess = {
                        reduce {
                            state.copy(
                                isAddingFeed = false,
                                isAddSheetVisible = false,
                                addUrlInput = "",
                                addFeedError = null,
                            )
                        }
                        // addFeed only persists feed metadata; pull the new feed's articles so its
                        // row resolves instead of spinning on "loading" forever.
                        triggerRefresh(initial = false)
                    },
                    onFailure = {
                        reduce {
                            state.copy(
                                isAddingFeed = false,
                                addFeedError = AddFeedError.COULD_NOT_ADD,
                            )
                        }
                    },
                )
            submitting = false
        }
    }

    // --- Delete feed ---

    fun requestDeleteFeed(id: FeedId) {
        intent { reduce { state.copy(pendingDeletion = id) } }
    }

    fun cancelDeleteFeed() {
        intent { reduce { state.copy(pendingDeletion = null) } }
    }

    /**
     * Confirm deletion of [OrbitFeedListState.pendingDeletion]; the row disappears via the
     * observer.
     */
    fun confirmDeleteFeed() {
        intent {
            val id = state.pendingDeletion ?: return@intent
            reduce { state.copy(pendingDeletion = null) }
            feedRepository.removeFeed(id).onFailure {
                postSideEffect(FeedListSideEffect.DeleteFailed)
            }
        }
    }

    // --- onCreate helpers (run inside the container's coroutine, with the Syntax as receiver) ---

    private suspend fun Syntax<OrbitFeedListState, FeedListSideEffect>.observeFeeds() =
        coroutineScope {
            var articleJobs: List<Job> = emptyList()
            feedRepository.observeFeeds().collect { feeds ->
                reduce { state.copy(items = reconcileRows(state.items, feeds)) }
                // Restart the per-feed article collectors: cancel the previous round and launch one
                // independent collector per feed so rows resolve as their data arrives.
                articleJobs.forEach { it.cancel() }
                articleJobs = feeds.map { feed -> launch { observeLatestArticle(feed) } }
            }
        }

    private suspend fun Syntax<OrbitFeedListState, FeedListSideEffect>.observeLatestArticle(
        feed: Feed
    ) {
        articleRepository.observeArticles(feed.id).collect { articles ->
            // Empty means "not fetched yet" (the SSOT cache starts empty); keep the row loading
            // until articles actually arrive. A genuinely empty feed is resolved when the
            // surrounding refresh completes (see triggerRefresh).
            if (articles.isEmpty()) return@collect
            // Latest = newest publishedAt; undated articles sort oldest and ties break by id, so
            // the choice is deterministic (kotlinx Instant? compares null as the smallest value).
            val latest = articles.maxWithOrNull(compareBy({ it.publishedAt }, { it.id.value }))
            reduce {
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

    private suspend fun Syntax<OrbitFeedListState, FeedListSideEffect>.triggerRefresh(
        initial: Boolean
    ) {
        if (refreshing) return
        refreshing = true
        reduce {
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
                    // Refresh persisted everything (see FeedRepository.refreshAllFeeds): any row
                    // still loading simply has no articles.
                    reduce {
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            items = state.items.map { it.copy(isArticleLoading = false) },
                        )
                    }
                },
                onFailure = {
                    // Keep existing rows visible: surface the failure full-screen only when nothing
                    // is shown yet, otherwise as a one-shot side effect (snackbar).
                    val hadRows = state.items.isNotEmpty()
                    reduce {
                        state.copy(isLoading = false, isRefreshing = false, isError = !hadRows)
                    }
                    if (hadRows) postSideEffect(FeedListSideEffect.RefreshFailed)
                },
            )
        refreshing = false
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
}
