package com.github.appachan.rssreader.feature.feed

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Controllable in-memory fake for ViewModel tests. Mirrors the production fake's dependency-ordered
 * parallel fetch (feed list first, then per-feed articles in parallel with a staggered delay) but
 * lets each test inject the data, delays and an error toggle so state transitions can be driven
 * with virtual time.
 */
class FakeFeedDataSource(
    private val feeds: List<Feed>,
    private val articles: Map<FeedId, List<Article>> = emptyMap(),
    var failRefresh: Boolean = false,
    private val feedListDelayMs: Long = 0L,
    private val articleBaseDelayMs: Long = 0L,
    private val articleStepDelayMs: Long = 0L,
) : FeedRepository, ArticleRepository {

    private val feedsFlow = MutableStateFlow<List<Feed>>(emptyList())
    private val articlesFlow = MutableStateFlow<Map<Long, List<Article>>>(emptyMap())

    override fun observeFeeds(): Flow<List<Feed>> = feedsFlow.asStateFlow()

    override fun observeFeed(id: FeedId): Flow<Feed?> = feedsFlow.map { list ->
        list.firstOrNull { it.id == id }
    }

    override fun observeArticles(feedId: FeedId): Flow<List<Article>> = articlesFlow.map {
        it[feedId.value].orEmpty()
    }

    override fun observeAllArticles(): Flow<List<Article>> = articlesFlow.map {
        it.values.flatten()
    }

    override fun observeArticle(id: ArticleId): Flow<Article?> = articlesFlow.map { byFeed ->
        byFeed.values.flatten().firstOrNull { it.id == id }
    }

    override suspend fun refreshAllFeeds(): Result<Unit> {
        if (failRefresh) {
            return Result.failure(IllegalStateException("refresh failed"))
        }
        delay(feedListDelayMs)
        feedsFlow.value = feeds
        coroutineScope {
            feeds.forEachIndexed { index, feed ->
                launch {
                    delay(articleBaseDelayMs + index * articleStepDelayMs)
                    articlesFlow.update { it + (feed.id.value to articles[feed.id].orEmpty()) }
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun refreshFeed(id: FeedId): Result<Unit> = refreshAllFeeds()

    override suspend fun addFeed(url: String): Result<FeedId> = Result.success(FeedId(0L))

    override suspend fun removeFeed(id: FeedId): Result<Unit> = Result.success(Unit)

    override suspend fun markRead(id: ArticleId): Result<Unit> = Result.success(Unit)

    override suspend fun markUnread(id: ArticleId): Result<Unit> = Result.success(Unit)
}
