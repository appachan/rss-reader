package com.github.appachan.rssreader.feature.feed.reducer

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId

/**
 * The pure heart of pattern ③: `(state, action) -> next state`, no I/O and no side effects, so the
 * whole state machine is verifiable with plain value assertions (see `FeedReducerTest`). The two
 * dialogs delegate to focused sub-reducers to keep this top-level dispatch small.
 */
fun reduce(state: ReducerFeedState, action: FeedAction): ReducerFeedState =
    when (action) {
        FeedAction.LoadStarted -> state.copy(isLoading = true, isError = false)
        FeedAction.RefreshStarted -> state.copy(isRefreshing = true, isError = false)
        is FeedAction.FeedsObserved -> state.copy(items = reconcileRows(state.items, action.feeds))
        is FeedAction.ArticleResolved ->
            state.copy(items = resolveArticle(state.items, action.feedId, action.latest))
        FeedAction.RefreshSucceeded ->
            state.copy(
                isLoading = false,
                isRefreshing = false,
                items = state.items.map { it.copy(isArticleLoading = false) },
            )
        // Keep existing rows visible: a full-screen error is shown only when nothing is on screen,
        // otherwise the ViewModel emits a one-shot snackbar event instead.
        FeedAction.RefreshFailed ->
            state.copy(isLoading = false, isRefreshing = false, isError = state.items.isEmpty())
        is FeedAction.AddFeed -> reduceAddFeed(state, action)
        is FeedAction.Delete -> reduceDelete(state, action)
    }

private fun reduceAddFeed(state: ReducerFeedState, action: FeedAction.AddFeed): ReducerFeedState =
    when (action) {
        FeedAction.AddFeed.SheetOpened ->
            state.copy(isAddSheetVisible = true, addUrlInput = "", addFeedError = null)
        FeedAction.AddFeed.SheetDismissed ->
            state.copy(
                isAddSheetVisible = false,
                addUrlInput = "",
                addFeedError = null,
                isAddingFeed = false,
            )
        // Clear the error as soon as the user edits, so it isn't shown against new input.
        is FeedAction.AddFeed.UrlChanged ->
            state.copy(addUrlInput = action.url, addFeedError = null)
        FeedAction.AddFeed.Started -> state.copy(isAddingFeed = true, addFeedError = null)
        FeedAction.AddFeed.Succeeded ->
            state.copy(
                isAddingFeed = false,
                isAddSheetVisible = false,
                addUrlInput = "",
                addFeedError = null,
            )
        is FeedAction.AddFeed.Failed ->
            state.copy(isAddingFeed = false, addFeedError = action.error)
    }

private fun reduceDelete(state: ReducerFeedState, action: FeedAction.Delete): ReducerFeedState =
    when (action) {
        is FeedAction.Delete.Requested -> state.copy(pendingDeletion = action.id)
        // Cancel and confirm both close the dialog; confirm's actual deletion is a side effect run
        // by the ViewModel, not a state change.
        FeedAction.Delete.Cancelled,
        FeedAction.Delete.Confirmed -> state.copy(pendingDeletion = null)
    }

/**
 * Merge the freshly observed [feeds] into the current rows, preserving an already-resolved article
 * so a refresh doesn't flip a row back to loading. New feeds start in the loading state.
 */
private fun reconcileRows(current: List<FeedRowUiModel>, feeds: List<Feed>): List<FeedRowUiModel> {
    val existing = current.associateBy { it.feedId }
    return feeds.map { feed ->
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

private fun resolveArticle(
    items: List<FeedRowUiModel>,
    feedId: FeedId,
    latest: Article,
): List<FeedRowUiModel> = items.map { row ->
    if (row.feedId == feedId) {
        row.copy(
            latestArticleTitle = latest.title,
            latestArticlePublishedAt = latest.publishedAt,
            isArticleLoading = false,
        )
    } else {
        row
    }
}
