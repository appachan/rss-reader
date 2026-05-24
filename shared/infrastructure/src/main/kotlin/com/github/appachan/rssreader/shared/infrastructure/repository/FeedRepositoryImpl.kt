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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * SSOT-backed [FeedRepository]: observations stream from SQLDelight; [addFeed] validates the URL by
 * fetching and parsing it, then persists the feed.
 *
 * [refreshFeed]/[refreshAllFeeds] are no-ops: refresh exists to pull articles, and feed metadata is
 * captured at [addFeed] time, so there is nothing to refresh.
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
                check(queries.selectByUrl(normalized).executeAsOneOrNull() == null) {
                    "Feed already subscribed: $normalized"
                }
                val remote = feedFetcher.fetch(normalized)
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

    override suspend fun removeFeed(id: FeedId): Result<Unit> =
        withContext(ioDispatcher) { runCatching { queries.deleteById(id.value) }.map {} }

    override suspend fun refreshFeed(id: FeedId): Result<Unit> = Result.success(Unit)

    override suspend fun refreshAllFeeds(): Result<Unit> = Result.success(Unit)
}
