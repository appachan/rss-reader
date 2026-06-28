package com.github.appachan.rssreader.shared.infrastructure.local

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.infrastructure.db.Article as ArticleRow
import kotlinx.datetime.Instant

/**
 * Maps a stored row to the domain model, converting epoch-millis back to [Instant]. [isRead] is
 * fixed to false until #14 adds the `ArticleReadState` table and a LEFT JOIN projection.
 */
internal fun Article.Companion.from(row: ArticleRow): Article =
    Article(
        id = ArticleId(row.id),
        feedId = FeedId(row.feed_id),
        guid = row.guid,
        title = row.title,
        url = row.url,
        author = row.author,
        summary = row.summary,
        content = row.content,
        publishedAt = row.published_at?.let { Instant.fromEpochMilliseconds(it) },
        isRead = false,
    )
