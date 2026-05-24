package com.github.appachan.rssreader.shared.domain.repository

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.FeedId
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    fun observeArticles(feedId: FeedId): Flow<List<Article>>

    fun observeAllArticles(): Flow<List<Article>>

    fun observeArticle(id: ArticleId): Flow<Article?>

    suspend fun markRead(id: ArticleId): Result<Unit>

    suspend fun markUnread(id: ArticleId): Result<Unit>
}
