package com.github.appachan.rssreader.shared.infrastructure.remote

import com.prof18.rssparser.RssParser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Fetches the raw feed over Ktor and parses it with prof18/RSS-Parser. A non-success status fails
 * the Ktor call (`expectSuccess = true`); an unparseable body throws from [RssParser.parse]. Both
 * surface as a thrown exception, per the [FeedFetcher] contract.
 */
internal class RssParserFeedFetcher(
    private val httpClient: HttpClient,
    private val rssParser: RssParser,
) : FeedFetcher {
    override suspend fun fetch(url: String): RemoteFeed {
        val raw = httpClient.get(url).bodyAsText()
        val channel = rssParser.parse(raw)
        return RemoteFeed(
            title = channel.title,
            siteUrl = channel.link,
            description = channel.description,
        )
    }
}
