package com.github.appachan.rssreader.feature.feed

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.github.appachan.rssreader.shared.domain.model.FeedId
import kotlinx.datetime.Instant

private fun previewRow(
    id: Long,
    title: String,
    article: String?,
    loading: Boolean,
): FeedRowUiModel =
    FeedRowUiModel(
        feedId = FeedId(id),
        title = title,
        latestArticleTitle = article,
        latestArticlePublishedAt =
            if (article == null) null
            else Instant.fromEpochMilliseconds(epochMilliseconds = 1_716_500_000_000L),
        isArticleLoading = loading,
    )

@Preview
@Composable
private fun FeedListLoadedPreview() {
    MaterialTheme {
        FeedListScreen(
            state =
                FeedListUiState(
                    items =
                        listOf(
                            previewRow(
                                id = 1,
                                title = "Android Developers Blog",
                                article = "Compose 1.9 released",
                                loading = false,
                            ),
                            previewRow(
                                id = 2,
                                title = "Kotlin Blog",
                                article = null,
                                loading = true,
                            ),
                            previewRow(id = 3, title = "空フィード", article = null, loading = false),
                        )
                ),
            onRefresh = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun FeedListRefreshingPreview() {
    MaterialTheme {
        FeedListScreen(
            state =
                FeedListUiState(
                    items =
                        listOf(
                            previewRow(
                                id = 1,
                                title = "Android Developers Blog",
                                article = "Compose 1.9 released",
                                loading = false,
                            )
                        ),
                    isRefreshing = true,
                ),
            onRefresh = {},
            onRetry = {},
        )
    }
}

@Preview
@Composable
private fun FeedListLoadingPreview() {
    MaterialTheme {
        FeedListScreen(state = FeedListUiState(isLoading = true), onRefresh = {}, onRetry = {})
    }
}

@Preview
@Composable
private fun FeedListErrorPreview() {
    MaterialTheme {
        FeedListScreen(
            state = FeedListUiState(error = "フィードの更新に失敗しました"),
            onRefresh = {},
            onRetry = {},
        )
    }
}
