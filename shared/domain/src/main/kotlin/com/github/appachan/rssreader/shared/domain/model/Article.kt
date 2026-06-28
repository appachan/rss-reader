package com.github.appachan.rssreader.shared.domain.model

import kotlinx.datetime.Instant

data class Article(
    val id: ArticleId,
    val feedId: FeedId,
    val guid: String,
    val title: String,
    val url: String,
    val author: String?,
    val summary: String?,
    val content: String?,
    val publishedAt: Instant?,
    val isRead: Boolean,
) {
    companion object
}
