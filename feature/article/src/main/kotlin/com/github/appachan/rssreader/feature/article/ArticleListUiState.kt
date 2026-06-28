package com.github.appachan.rssreader.feature.article

import com.github.appachan.rssreader.shared.domain.model.ArticleId
import kotlinx.datetime.Instant

/**
 * Flat UiState for a single feed's article list: loading/refreshing/error are plain flags, no Async
 * wrapper.
 *
 * [isError] is a presentation-agnostic flag; the message lives in string resources and is resolved
 * by the screen.
 */
data class ArticleListUiState(
    val feedTitle: String = "",
    val items: List<ArticleRowUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isError: Boolean = false,
)

/** A single article row: title, an optional summary snippet, and an optional published date. */
data class ArticleRowUiModel(
    val articleId: ArticleId,
    val title: String,
    val summary: String?,
    val publishedAt: Instant?,
)

/** One-shot events shown once and not re-emitted on recomposition. */
sealed interface ArticleListEvent {
    /** A refresh failed while articles were already on screen; show a transient message. */
    data object RefreshFailed : ArticleListEvent
}

/** The screen's callbacks bundled into one hoistable object. Defaults are no-ops for previews. */
data class ArticleListActions(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)
