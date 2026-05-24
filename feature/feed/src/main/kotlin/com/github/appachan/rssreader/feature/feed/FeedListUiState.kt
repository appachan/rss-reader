package com.github.appachan.rssreader.feature.feed

import com.github.appachan.rssreader.shared.domain.model.FeedId
import kotlinx.datetime.Instant

/**
 * Pattern ① state: a flat UiState that holds [feeds][items], [isLoading], [isRefreshing] and
 * [error] directly as fields, without an Async wrapper type. The later pattern-comparison tasks
 * (TASK-011〜014) re-implement the same screen with different state-management techniques.
 */
data class FeedListUiState(
    val items: List<FeedRowUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

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
