package com.github.appachan.rssreader.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.appachan.rssreader.shared.domain.model.FeedId
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedListScreen(modifier: Modifier = Modifier, viewModel: FeedListViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailedMessage = stringResource(R.string.feed_list_error)
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                FeedListEvent.RefreshFailed -> snackbarHostState.showSnackbar(refreshFailedMessage)
            }
        }
    }

    FeedListScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedListScreen(
    uiState: FeedListUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> LoadingContent()
                uiState.isError && uiState.items.isEmpty() -> ErrorContent(onRetry)
                else -> FeedList(uiState.items)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(all = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.feed_list_error),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(text = stringResource(R.string.feed_list_retry))
        }
    }
}

@Composable
private fun FeedList(items: List<FeedRowUiModel>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items = items, key = { _, row -> row.feedId.value }) { index, row ->
            FeedRow(row)
            if (index < items.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun FeedRow(row: FeedRowUiModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = row.title, style = MaterialTheme.typography.titleMedium)
        if (row.isArticleLoading) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size = 16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.feed_list_article_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            Text(
                text = row.latestArticleTitle ?: stringResource(R.string.feed_list_no_articles),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            row.latestArticlePublishedAt?.let { published ->
                Text(
                    text = formatPublishedDate(published),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun formatPublishedDate(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

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
        Surface {
            FeedListScreen(
                uiState =
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
                                previewRow(
                                    id = 3,
                                    title = "Empty feed",
                                    article = null,
                                    loading = false,
                                ),
                            )
                    ),
                onRefresh = {},
                onRetry = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}

@Preview
@Composable
private fun FeedListRefreshingPreview() {
    MaterialTheme {
        Surface {
            FeedListScreen(
                uiState =
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
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}

@Preview
@Composable
private fun FeedListLoadingPreview() {
    MaterialTheme {
        Surface {
            FeedListScreen(
                uiState = FeedListUiState(isLoading = true),
                onRefresh = {},
                onRetry = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}

@Preview
@Composable
private fun FeedListErrorPreview() {
    MaterialTheme {
        Surface {
            FeedListScreen(
                uiState = FeedListUiState(isError = true),
                onRefresh = {},
                onRetry = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}
