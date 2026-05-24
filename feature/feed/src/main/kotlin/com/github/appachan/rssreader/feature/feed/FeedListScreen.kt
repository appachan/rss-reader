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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
    val deleteFailedMessage = stringResource(R.string.feed_delete_failed)
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                FeedListEvent.RefreshFailed -> snackbarHostState.showSnackbar(refreshFailedMessage)
                FeedListEvent.DeleteFailed -> snackbarHostState.showSnackbar(deleteFailedMessage)
            }
        }
    }

    FeedListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        actions =
            FeedListActions(
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onAddClick = viewModel::showAddSheet,
                onAddDismiss = viewModel::dismissAddSheet,
                onAddUrlChange = viewModel::onAddUrlChange,
                onAddSubmit = viewModel::submitAddFeed,
                onRequestDelete = viewModel::requestDeleteFeed,
                onCancelDelete = viewModel::cancelDeleteFeed,
                onConfirmDelete = viewModel::confirmDeleteFeed,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedListScreen(
    uiState: FeedListUiState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    actions: FeedListActions = FeedListActions(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_list_title)) },
                actions = {
                    IconButton(onClick = actions.onAddClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.feed_add_action),
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
                else -> FeedList(uiState.items, actions.onRequestDelete)
            }
        }
    }

    if (uiState.isAddSheetVisible) {
        AddFeedDialog(
            url = uiState.addUrlInput,
            isAdding = uiState.isAddingFeed,
            error = uiState.addFeedError,
            actions = actions,
        )
    }

    uiState.pendingDeletion?.let { pendingId ->
        DeleteConfirmDialog(
            feedTitle = uiState.items.firstOrNull { it.feedId == pendingId }?.title.orEmpty(),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onCancelDelete,
        )
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
private fun FeedList(items: List<FeedRowUiModel>, onRequestDelete: (FeedId) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items = items, key = { _, row -> row.feedId.value }) { index, row ->
            FeedRow(row, onRequestDelete)
            if (index < items.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun FeedRow(row: FeedRowUiModel, onRequestDelete: (FeedId) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
                        text =
                            published
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                .date
                                .toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        IconButton(onClick = { onRequestDelete(row.feedId) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.feed_delete_action),
            )
        }
    }
}

// Not annotated @Preview: an OutlinedTextField inside a Dialog never reaches idle under
// Robolectric, so Roborazzi can't capture it. The add dialog is verified on-device; the list
// (TopAppBar + per-row delete) and the delete-confirm dialog are covered by previews below.
@Composable
private fun AddFeedDialog(
    url: String,
    isAdding: Boolean,
    error: AddFeedError?,
    actions: FeedListActions,
) {
    val errorMessage =
        when (error) {
            AddFeedError.INVALID_URL -> stringResource(R.string.feed_add_error_invalid_url)
            AddFeedError.COULD_NOT_ADD -> stringResource(R.string.feed_add_error_could_not_add)
            null -> null
        }
    AlertDialog(
        onDismissRequest = actions.onAddDismiss,
        title = { Text(stringResource(R.string.feed_add_title)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = actions.onAddUrlChange,
                label = { Text(stringResource(R.string.feed_add_url_label)) },
                singleLine = true,
                enabled = !isAdding,
                isError = errorMessage != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = errorMessage?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = actions.onAddSubmit, enabled = !isAdding) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(size = 16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.feed_add_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onAddDismiss, enabled = !isAdding) {
                Text(stringResource(R.string.feed_add_cancel))
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(feedTitle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_delete_title)) },
        text = { Text(stringResource(R.string.feed_delete_message, feedTitle)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.feed_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_delete_cancel)) }
        },
    )
}

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

private fun previewItems(): List<FeedRowUiModel> =
    listOf(
        previewRow(
            id = 1,
            title = "Android Developers Blog",
            article = "Compose 1.9 released",
            loading = false,
        ),
        previewRow(id = 2, title = "Kotlin Blog", article = null, loading = true),
        previewRow(id = 3, title = "Empty feed", article = null, loading = false),
    )

@Preview
@Composable
private fun FeedListLoadedPreview() {
    MaterialTheme {
        Surface {
            FeedListScreen(
                uiState = FeedListUiState(items = previewItems()),
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
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}

@Preview
@Composable
private fun FeedListDeleteConfirmPreview() {
    MaterialTheme {
        Surface {
            FeedListScreen(
                uiState = FeedListUiState(items = previewItems(), pendingDeletion = FeedId(1)),
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}
