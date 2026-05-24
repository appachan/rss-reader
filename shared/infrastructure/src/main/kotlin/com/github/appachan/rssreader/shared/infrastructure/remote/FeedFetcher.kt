package com.github.appachan.rssreader.shared.infrastructure.remote

/**
 * Fetches a feed's channel-level metadata to validate a subscription at registration time.
 * Implementations throw when the URL cannot be retrieved or its body is not a valid RSS/Atom feed;
 * the repository turns that into a `Result.failure`.
 */
internal interface FeedFetcher {
    suspend fun fetch(url: String): RemoteFeed
}

/** Channel-level metadata extracted from a fetched feed. */
internal data class RemoteFeed(val title: String?, val siteUrl: String?, val description: String?)
