package com.github.appachan.rssreader.shared.domain.repository

import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import kotlinx.coroutines.flow.Flow

interface FeedRepository {
    fun observeFeeds(): Flow<List<Feed>>

    fun observeFeed(id: FeedId): Flow<Feed?>

    suspend fun addFeed(url: String): Result<FeedId>

    suspend fun removeFeed(id: FeedId): Result<Unit>

    suspend fun refreshFeed(id: FeedId): Result<Unit>

    suspend fun refreshAllFeeds(): Result<Unit>
}
