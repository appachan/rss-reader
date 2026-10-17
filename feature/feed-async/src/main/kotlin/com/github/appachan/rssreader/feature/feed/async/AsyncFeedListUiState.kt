package com.github.appachan.rssreader.feature.feed.async

import com.github.appachan.rssreader.shared.domain.model.FeedId
import kotlinx.datetime.Instant

/**
 * Pattern ② building block: a partial-state wrapper (Mavericks-style) describing where a single
 * asynchronously-loaded value is in its lifecycle. Each [AsyncFeedListUiState] field that comes
 * from a data source is wrapped in [Async] and the sources are merged with `combine`, so a screen
 * can render "loading / loaded / failed" per field instead of from a single screen-wide flag.
 *
 * `Refreshing` is deliberately *not* a member: keeping `data` in both `Success` and a hypothetical
 * `Refreshing` would force every `when` to handle the value twice, so pull-to-refresh is tracked by
 * a flat [AsyncFeedListUiState.isRefreshing] Boolean instead (the documented 妥協点 of this pattern).
 */
sealed interface Async<out T> {
    /** Nothing has been requested yet (the state before the first load). */
    data object Idle : Async<Nothing>

    /** A load is in flight and no value is available to show. */
    data object Loading : Async<Nothing>

    /** A value is available; [data] is the latest successful result. */
    data class Success<T>(val data: T) : Async<T>

    /**
     * The load failed; [error] is kept for diagnostics (UiState is never persisted, so a
     * non-Parcelable Throwable is fine — recovery is a re-fetch, not state restoration).
     */
    data class Failure(val error: Throwable) : Async<Nothing>
}

/**
 * Pattern ② state: the feed list itself is an [Async] field (so loading / loaded / error is encoded
 * in the type rather than in separate `isLoading` / `isError` flags), while pull-to-refresh and the
 * add/delete dialogs stay as flat fields. The same screen as pattern ① renders this, so the look
 * and acceptance criteria are unchanged — only the state-management technique differs.
 */
data class AsyncFeedListUiState(
    val feeds: Async<List<FeedRowUiModel>> = Async.Idle,
    val isRefreshing: Boolean = false,
    // Add-feed dialog.
    val isAddSheetVisible: Boolean = false,
    val addUrlInput: String = "",
    val isAddingFeed: Boolean = false,
    val addFeedError: AddFeedError? = null,
    // The feed awaiting delete confirmation, or null when no dialog is shown.
    val pendingDeletion: FeedId? = null,
)

/** Why adding a feed failed, resolved to a message by the screen. */
enum class AddFeedError {
    /** The input is not a usable http(s) URL (rejected locally before any network call). */
    INVALID_URL,

    /** Fetching or parsing the feed failed (unreachable, not an RSS/Atom feed, or duplicate). */
    COULD_NOT_ADD,
}

/**
 * A single feed row. The latest article is its own [Async] field, filled in independently per feed
 * via `combine`, so each row resolves from [Async.Loading] to [Async.Success] as that feed's
 * article arrives (取得できた行から順次表示) without blocking the others.
 */
data class FeedRowUiModel(
    val feedId: FeedId,
    val title: String,
    val latestArticle: Async<LatestArticle?>,
)

/** The newest article of a feed, or `null` (as [Async.Success]) when the feed has none. */
data class LatestArticle(val title: String, val publishedAt: Instant?)

/**
 * One-shot UI events that must be shown once and not re-emitted on recomposition. Distinct from
 * [AsyncFeedListUiState], which is the durable screen state the UI renders directly.
 */
sealed interface FeedListEvent {
    /** A refresh failed while rows were already on screen; show a transient message (snackbar). */
    data object RefreshFailed : FeedListEvent

    /** Deleting a feed failed; show a transient message (snackbar). */
    data object DeleteFailed : FeedListEvent
}

/**
 * The screen's callbacks bundled into one object so the stateless composable stays a small,
 * hoistable surface instead of a long parameter list. Defaults are no-ops for previews.
 */
data class FeedListActions(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAddClick: () -> Unit = {},
    val onAddDismiss: () -> Unit = {},
    val onAddUrlChange: (String) -> Unit = {},
    val onAddSubmit: () -> Unit = {},
    val onRequestDelete: (FeedId) -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    // Navigation to a feed's article list. Supplied by the app shell (not the ViewModel).
    val onFeedClick: (FeedId) -> Unit = {},
)
