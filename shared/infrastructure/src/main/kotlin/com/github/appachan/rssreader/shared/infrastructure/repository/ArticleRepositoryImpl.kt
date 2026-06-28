package com.github.appachan.rssreader.shared.infrastructure.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.infrastructure.db.FeedDatabase
import com.github.appachan.rssreader.shared.infrastructure.local.from
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SSOT-backed [ArticleRepository]: observations stream rows from SQLDelight (newest first), written
 * by `FeedRepository.refreshFeed`.
 *
 * Read-state commands are unsupported until #14 adds the `ArticleReadState` table; [Article.isRead]
 * is projected as false by the mapper for now.
 */
internal class ArticleRepositoryImpl(
    private val database: FeedDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : ArticleRepository {

    private val queries
        get() = database.articleQueries

    override fun observeArticles(feedId: FeedId): Flow<List<Article>> =
        queries.selectByFeed(feedId.value).asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { Article.from(it) }
        }

    override fun observeAllArticles(): Flow<List<Article>> =
        queries.selectAll().asFlow().mapToList(ioDispatcher).map { rows ->
            rows.map { Article.from(it) }
        }

    override fun observeArticle(id: ArticleId): Flow<Article?> =
        queries.selectById(id.value).asFlow().mapToOneOrNull(ioDispatcher).map {
            it?.let { row -> Article.from(row) }
        }

    override suspend fun markRead(id: ArticleId): Result<Unit> = unsupported()

    override suspend fun markUnread(id: ArticleId): Result<Unit> = unsupported()

    private fun unsupported(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Read state is not supported yet"))
}
