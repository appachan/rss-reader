package com.github.appachan.rssreader.shared.infrastructure.repository

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Placeholder article store: there is no `Article` table yet, so observations are empty and read
 * commands are unsupported.
 */
internal class ArticleRepositoryImpl : ArticleRepository {
    override fun observeArticles(feedId: FeedId): Flow<List<Article>> = flowOf(emptyList())

    override fun observeAllArticles(): Flow<List<Article>> = flowOf(emptyList())

    override fun observeArticle(id: ArticleId): Flow<Article?> = flowOf(null)

    override suspend fun markRead(id: ArticleId): Result<Unit> = unsupported()

    override suspend fun markUnread(id: ArticleId): Result<Unit> = unsupported()

    private fun unsupported(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Read state is not supported yet"))
}
