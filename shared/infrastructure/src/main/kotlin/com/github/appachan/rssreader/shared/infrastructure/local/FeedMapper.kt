package com.github.appachan.rssreader.shared.infrastructure.local

import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.infrastructure.db.Feed as FeedRow
import kotlinx.datetime.Instant

internal fun Feed.Companion.from(row: FeedRow): Feed =
    Feed(
        id = FeedId(row.id),
        url = row.url,
        title = row.title,
        siteUrl = row.site_url,
        description = row.description,
        lastFetchedAt = row.last_fetched_at?.let { Instant.fromEpochMilliseconds(it) },
    )
