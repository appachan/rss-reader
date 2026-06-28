package com.github.appachan.rssreader.shared.infrastructure.remote

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Fetches the raw feed over Ktor and parses it with prof18/RSS-Parser. A non-success status fails
 * the Ktor call (`expectSuccess = true`); an unparseable body throws from [RssParser.parse]. Both
 * surface as a thrown exception, per the [FeedFetcher] contract.
 *
 * The parser returns nullable fields; [toRemoteArticle] applies the domain's identity/title
 * fallbacks here so the mapper to `Article` is a straight copy and the fallback logic lives in one
 * place (foundation-design §3).
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
            articles = channel.items.map { it.toRemoteArticle() },
        )
    }
}

private fun RssItem.toRemoteArticle(): RemoteArticle {
    val link = link?.takeIf { it.isNotBlank() }
    // guid (<guid>/<id>) is the spec's stable id; fall back to the link, then to a content-derived
    // key so the same article keeps one identity across refreshes (index is unstable, so unused).
    val guid = guid?.takeIf { it.isNotBlank() } ?: link ?: "${title.orEmpty()}|${pubDate.orEmpty()}"
    return RemoteArticle(
        guid = guid,
        title = title?.takeIf { it.isNotBlank() } ?: link ?: guid,
        url = link ?: guid,
        author = author,
        summary = stripHtml(description),
        content = content,
        publishedAt = parsePubDate(pubDate),
    )
}
