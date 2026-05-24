package com.github.appachan.rssreader.feature.feed

import com.github.appachan.rssreader.shared.domain.model.FeedId
import kotlinx.datetime.Instant

/**
 * Pattern ① state: a flat UiState that holds [feeds][items], [isLoading], [isRefreshing] and
 * [isError] directly as fields, without an Async wrapper type. The later pattern-comparison tasks
 * (TASK-011〜014) re-implement the same screen with different state-management techniques.
 *
 * [isError] / [addFeedError] are presentation-agnostic flags, not messages: the user-facing error
 * text lives in string resources and is resolved by the screen, so the ViewModel holds no display
 * strings.
 */
data class FeedListUiState(
    val items: List<FeedRowUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isError: Boolean = false,
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
 * A single feed row. [latestArticleTitle] / [latestArticlePublishedAt] are filled in independently
 * once that feed's latest article has been fetched; until then [isArticleLoading] is true so the
 * row can show inline progress (取得できた行から順次表示).
 */
data class FeedRowUiModel(
    val feedId: FeedId,
    val title: String,
    val latestArticleTitle: String?,
    val latestArticlePublishedAt: Instant?,
    val isArticleLoading: Boolean,
)

/**
 * One-shot UI events that must be shown once and not re-emitted on recomposition. Distinct from
 * [FeedListUiState], which is the durable screen state the UI renders directly.
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
)
