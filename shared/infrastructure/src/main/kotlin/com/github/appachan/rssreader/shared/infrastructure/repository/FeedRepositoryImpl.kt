package com.github.appachan.rssreader.shared.infrastructure.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import com.github.appachan.rssreader.shared.infrastructure.db.FeedDatabase
import com.github.appachan.rssreader.shared.infrastructure.local.from
import com.github.appachan.rssreader.shared.infrastructure.remote.FeedFetcher
import com.github.appachan.rssreader.shared.infrastructure.remote.RemoteArticle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * SSOT-backed [FeedRepository]: observations stream from SQLDelight; [addFeed] validates the URL by
 * fetching and parsing it, then persists the feed.
 *
 * [refreshFeed]/[refreshAllFeeds] fetch each feed and upsert its articles; the article observers
 * then re-emit. The upsert keeps article identity stable (see Article.sq) rather than
 * delete+reinsert.
 */
internal class FeedRepositoryImpl(
    private val database: FeedDatabase,
    private val feedFetcher: FeedFetcher,
    private val ioDispatcher: CoroutineDispatcher,
) : FeedRepository {

    private val queries
        get() = database.feedQueries

    override fun observeFeeds(): Flow<List<Feed>> =
        queries.selectAll().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { Feed.from(it) }
        }

    override fun observeFeed(id: FeedId): Flow<Feed?> =
        queries.selectById(id.value).asFlow().mapToOneOrNull(ioDispatcher).map {
            it?.let { row -> Feed.from(row) }
        }

    override suspend fun addFeed(url: String): Result<FeedId> =
        withContext(ioDispatcher) {
            runCatching {
                val normalized = url.trim()
                require(normalized.isNotEmpty()) { "Blank feed URL" }
                val remote = feedFetcher.fetch(normalized)
                // One transaction keeps the id read atomic with the insert and dedup check.
                database.transactionWithResult {
                    check(queries.selectByUrl(normalized).executeAsOneOrNull() == null) {
                        "Feed already subscribed: $normalized"
                    }
                    queries.insertFeed(
                        url = normalized,
                        title = remote.title?.takeIf { it.isNotBlank() } ?: normalized,
                        site_url = remote.siteUrl,
                        description = remote.description,
                        last_fetched_at = Clock.System.now().toEpochMilliseconds(),
                    )
                    FeedId(queries.lastInsertedId().executeAsOne())
                }
            }
        }

    override suspend fun removeFeed(id: FeedId): Result<Unit> =
        withContext(ioDispatcher) { runCatching { queries.deleteById(id.value) }.map {} }

    override suspend fun refreshFeed(id: FeedId): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val feed =
                    queries.selectById(id.value).executeAsOneOrNull()
                        ?: error("Feed not found: ${id.value}")
                persistArticles(id, feedFetcher.fetch(feed.url).articles)
            }
        }

    /**
     * Best-effort across all feeds: each feed is fetched independently, successes are persisted,
     * and the result is success if at least one feed refreshed (or there are none). It fails only
     * when every feed failed — so the feed-list summary stays resilient to a single broken feed,
     * while a total outage still surfaces. Per-feed errors are reported granularly by
     * [refreshFeed].
     */
    override suspend fun refreshAllFeeds(): Result<Unit> =
        withContext(ioDispatcher) {
            val feeds = queries.selectAll().executeAsList()
            if (feeds.isEmpty()) return@withContext Result.success(Unit)
            var anySuccess = false
            var lastError: Throwable? = null
            feeds.forEach { feed ->
                runCatching {
                        persistArticles(FeedId(feed.id), feedFetcher.fetch(feed.url).articles)
                    }
                    .onSuccess { anySuccess = true }
                    .onFailure { lastError = it }
            }
            if (anySuccess) Result.success(Unit)
            else Result.failure(lastError ?: IllegalStateException("All feeds failed to refresh"))
        }

    /** Upserts the fetched articles in one transaction (insert-or-ignore, then update payload). */
    private fun persistArticles(feedId: FeedId, articles: List<RemoteArticle>) {
        val articleQueries = database.articleQueries
        database.transaction {
            articles.forEach { article ->
                val publishedAt = article.publishedAt?.toEpochMilliseconds()
                articleQueries.insertOrIgnoreArticle(
                    feed_id = feedId.value,
                    guid = article.guid,
                    title = article.title,
                    url = article.url,
                    author = article.author,
                    summary = article.summary,
                    content = article.content,
                    published_at = publishedAt,
                )
                articleQueries.updateArticleByGuid(
                    title = article.title,
                    url = article.url,
                    author = article.author,
                    summary = article.summary,
                    content = article.content,
                    published_at = publishedAt,
                    feed_id = feedId.value,
                    guid = article.guid,
                )
            }
        }
    }
}
