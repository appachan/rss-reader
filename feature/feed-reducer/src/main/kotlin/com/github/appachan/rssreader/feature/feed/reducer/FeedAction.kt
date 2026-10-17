package com.github.appachan.rssreader.feature.feed.reducer

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId

/**
 * Everything that can change the feed-list state, as data. The ViewModel performs the side effects
 * (network, coroutines, job management) and dispatches one of these; [reduce] alone decides the
 * resulting state. This is the defining trait of pattern ③: the state-transition logic lives in one
 * pure function instead of being scattered across `_state.update { … }` call sites.
 *
 * The add-feed and delete dialogs group their actions under [AddFeed] / [Delete] so the reducer can
 * dispatch each group to a small focused sub-reducer.
 */
sealed interface FeedAction {
    /** Initial load began: show the full-screen spinner and clear any prior error. */
    data object LoadStarted : FeedAction

    /** Pull-to-refresh (or post-add) began: keep existing rows, show the refresh indicator. */
    data object RefreshStarted : FeedAction

    /**
     * The observed feed list changed; rows are reconciled, preserving already-resolved articles.
     */
    data class FeedsObserved(val feeds: List<Feed>) : FeedAction

    /** A feed's latest article resolved; that row stops loading and shows the article. */
    data class ArticleResolved(val feedId: FeedId, val latest: Article) : FeedAction

    /** Refresh finished: every still-loading row simply has no articles, so stop their spinners. */
    data object RefreshSucceeded : FeedAction

    /** Refresh failed: surface a full-screen error only when nothing is shown yet. */
    data object RefreshFailed : FeedAction

    /** Add-feed dialog transitions. */
    sealed interface AddFeed : FeedAction {
        data object SheetOpened : AddFeed

        data object SheetDismissed : AddFeed

        data class UrlChanged(val url: String) : AddFeed

        data object Started : AddFeed

        data object Succeeded : AddFeed

        data class Failed(val error: AddFeedError) : AddFeed
    }

    /** Delete-confirmation dialog transitions. */
    sealed interface Delete : FeedAction {
        data class Requested(val id: FeedId) : Delete

        data object Cancelled : Delete

        data object Confirmed : Delete
    }
}
