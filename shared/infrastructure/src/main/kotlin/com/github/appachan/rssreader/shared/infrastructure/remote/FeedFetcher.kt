package com.github.appachan.rssreader.shared.infrastructure.remote

import kotlinx.datetime.Instant

/**
 * Fetches a feed: its channel-level metadata (used to validate a subscription at registration time)
 * and its articles (persisted by a refresh). Implementations throw when the URL cannot be retrieved
 * or its body is not a valid RSS/Atom feed; the repository turns that into a `Result.failure`.
 */
internal interface FeedFetcher {
    suspend fun fetch(url: String): RemoteFeed
}

/**
 * A fetched feed. [articles] is empty for `addFeed` (which only needs the metadata) and populated
 * for a refresh.
 */
internal data class RemoteFeed(
    val title: String?,
    val siteUrl: String?,
    val description: String?,
    val articles: List<RemoteArticle> = emptyList(),
)

/**
 * One parsed article, already normalized to the domain's non-null shape: [guid] is the per-feed
 * dedup key (synthesized when the feed omits it) and [title]/[url] have fallbacks applied, so the
 * mapper to `Article` is a straight field copy.
 */
internal data class RemoteArticle(
    val guid: String,
    val title: String,
    val url: String,
    val author: String?,
    val summary: String?,
    val content: String?,
    val publishedAt: Instant?,
)
