package com.github.appachan.rssreader.feature.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.FeedId
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ArticleListScreen(
    feedId: FeedId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArticleListViewModel = koinViewModel { parametersOf(feedId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailedMessage = stringResource(R.string.article_list_error)
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                ArticleListEvent.RefreshFailed ->
                    snackbarHostState.showSnackbar(refreshFailedMessage)
            }
        }
    }

    ArticleListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        actions =
            ArticleListActions(
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onBack = onBack,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleListScreen(
    uiState: ArticleListUiState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    actions: ArticleListActions = ArticleListActions(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(uiState.feedTitle) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.article_list_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> LoadingContent()
                uiState.isError && uiState.items.isEmpty() -> ErrorContent(actions.onRetry)
                uiState.items.isEmpty() -> EmptyContent()
                else -> ArticleList(uiState.items)
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
            text = stringResource(R.string.article_list_error),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(text = stringResource(R.string.article_list_retry))
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.article_list_empty),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ArticleList(items: List<ArticleRowUiModel>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items = items, key = { _, row -> row.articleId.value }) { index, row ->
            ArticleRow(row)
            if (index < items.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun ArticleRow(row: ArticleRowUiModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = row.title, style = MaterialTheme.typography.titleMedium)
        row.summary
            ?.takeIf { it.isNotBlank() }
            ?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        row.publishedAt?.let { published ->
            Text(
                text = published.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun previewRow(
    id: Long,
    title: String,
    summary: String?,
    dated: Boolean = true,
): ArticleRowUiModel =
    ArticleRowUiModel(
        articleId = ArticleId(id),
        title = title,
        summary = summary,
        publishedAt =
            if (dated) Instant.fromEpochMilliseconds(epochMilliseconds = 1_716_500_000_000L)
            else null,
    )

private fun previewItems(): List<ArticleRowUiModel> =
    listOf(
        previewRow(
            id = 1,
            title = "Compose 1.9 released",
            summary = "A summary of what changed in the latest Compose release and why it matters.",
        ),
        previewRow(id = 2, title = "Kotlin 2.3 highlights", summary = null),
    )

@Preview
@Composable
private fun ArticleListLoadedPreview() {
    MaterialTheme {
        Surface {
            ArticleListScreen(
                uiState =
                    ArticleListUiState(
                        feedTitle = "Android Developers Blog",
                        items = previewItems(),
                    ),
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}

@Preview
@Composable
private fun ArticleListLoadingPreview() {
    MaterialTheme {
        Surface {
            ArticleListScreen(
                uiState =
                    ArticleListUiState(feedTitle = "Android Developers Blog", isLoading = true),
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}

@Preview
@Composable
private fun ArticleListEmptyPreview() {
    MaterialTheme {
        Surface {
            ArticleListScreen(
                uiState = ArticleListUiState(feedTitle = "Empty feed"),
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}

@Preview
@Composable
private fun ArticleListErrorPreview() {
    MaterialTheme {
        Surface {
            ArticleListScreen(
                uiState = ArticleListUiState(feedTitle = "Android Developers Blog", isError = true),
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}
