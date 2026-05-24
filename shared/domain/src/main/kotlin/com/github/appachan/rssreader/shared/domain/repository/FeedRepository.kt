package com.github.appachan.rssreader.shared.domain.repository

import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import kotlinx.coroutines.flow.Flow

interface FeedRepository {
    fun observeFeeds(): Flow<List<Feed>>

    fun observeFeed(id: FeedId): Flow<Feed?>

    suspend fun addFeed(url: String): Result<FeedId>

    suspend fun removeFeed(id: FeedId): Result<Unit>

    /**
     * Fetches the given feed and its articles and persists them to the single source of truth,
     * suspending until that persistence completes. On a successful return, [observeFeeds] and
     * [ArticleRepository.observeArticles] reflect the fetched data.
     */
    suspend fun refreshFeed(id: FeedId): Result<Unit>

    /**
     * Refreshes every feed: fetches each feed and its articles and persists them to the single
     * source of truth, suspending until persistence completes. Callers may treat a successful
     * return as "all feeds and their articles are now in the SSOT" — the feed-list screen relies on
     * this to finalize rows whose feed turned out to have no articles.
     */
    suspend fun refreshAllFeeds(): Result<Unit>
}
