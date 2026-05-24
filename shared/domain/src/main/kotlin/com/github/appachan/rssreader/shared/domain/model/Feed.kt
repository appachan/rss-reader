package com.github.appachan.rssreader.shared.domain.model

import kotlinx.datetime.Instant

data class Feed(
    val id: FeedId,
    val url: String,
    val title: String,
    val siteUrl: String?,
    val description: String?,
    val lastFetchedAt: Instant?,
)
