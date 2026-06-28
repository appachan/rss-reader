package com.github.appachan.rssreader.shared.infrastructure.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.appachan.rssreader.shared.infrastructure.db.FeedDatabase
import com.github.appachan.rssreader.shared.infrastructure.remote.FeedFetcher
import com.github.appachan.rssreader.shared.infrastructure.remote.RemoteArticle
import com.github.appachan.rssreader.shared.infrastructure.remote.RemoteFeed
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.Instant

/**
 * Pure-JVM tests for the article fetch→persist→observe pipeline, with an in-memory SQLDelight
 * driver (foreign keys enabled, matching production) and a fake [FeedFetcher]. The real fetch+parse
 * path (RssParserFeedFetcher) is exercised on-device, not here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleRepositoryImplTest :
    FunSpec({
        fun newDatabase(): FeedDatabase {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            // Match the production driver so ON DELETE CASCADE fires (FeedDatabase keeps one
            // in-memory connection, so a single PRAGMA suffices).
            driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON", parameters = 0)
            FeedDatabase.Schema.create(driver)
            return FeedDatabase(driver)
        }

        val dispatcher = UnconfinedTestDispatcher()

        fun feedRepo(db: FeedDatabase, fetcher: FeedFetcher) =
            FeedRepositoryImpl(db, fetcher, dispatcher)

        fun articleRepo(db: FeedDatabase) = ArticleRepositoryImpl(db, dispatcher)

        test("refreshFeed persists fetched articles, observed newest-first with undated last") {
            val db = newDatabase()
            val fetcher =
                FakeFeedFetcher(
                    articles =
                        listOf(
                            remoteArticle("a", publishedAtMs = 2_000),
                            remoteArticle("b", publishedAtMs = 3_000),
                            remoteArticle("c", publishedAtMs = null),
                        )
                )
            val feeds = feedRepo(db, fetcher)
            val id = feeds.addFeed("https://example.com/feed.xml").getOrThrow()

            feeds.refreshFeed(id).isSuccess shouldBe true

            val articles = articleRepo(db).observeArticles(id).first()
            articles.map { it.guid } shouldBe listOf("b", "a", "c")
        }

        test("refreshFeed upserts: re-refresh updates payload, keeps one row and a stable id") {
            val db = newDatabase()
            val fetcher = FakeFeedFetcher(articles = listOf(remoteArticle("g", title = "Old")))
            val feeds = feedRepo(db, fetcher)
            val id = feeds.addFeed("https://example.com/feed.xml").getOrThrow()
            feeds.refreshFeed(id)
            val firstId = articleRepo(db).observeArticles(id).first().single().id

            fetcher.articles = listOf(remoteArticle("g", title = "New"))
            feeds.refreshFeed(id)

            val articles = articleRepo(db).observeArticles(id).first()
            articles shouldHaveSize 1
            articles.single().id shouldBe firstId
            articles.single().title shouldBe "New"
        }

        test("removeFeed cascades: the feed's articles are deleted") {
            val db = newDatabase()
            val fetcher = FakeFeedFetcher(articles = listOf(remoteArticle("g")))
            val feeds = feedRepo(db, fetcher)
            val id = feeds.addFeed("https://example.com/feed.xml").getOrThrow()
            feeds.refreshFeed(id)
            db.articleQueries.selectAll().executeAsList() shouldHaveSize 1

            feeds.removeFeed(id).isSuccess shouldBe true

            db.articleQueries.selectAll().executeAsList() shouldHaveSize 0
        }

        test("refreshAllFeeds succeeds when at least one feed succeeds") {
            val db = newDatabase()
            val fetcher = FakeFeedFetcher(articles = listOf(remoteArticle("g")))
            val feeds = feedRepo(db, fetcher)
            val ok = feeds.addFeed("https://ok.example.com/feed.xml").getOrThrow()
            feeds.addFeed("https://bad.example.com/feed.xml")
            fetcher.failUrls = setOf("https://bad.example.com/feed.xml")

            feeds.refreshAllFeeds().isSuccess shouldBe true

            articleRepo(db).observeArticles(ok).first() shouldHaveSize 1
        }

        test("refreshAllFeeds fails when every feed fails") {
            val db = newDatabase()
            val fetcher = FakeFeedFetcher(articles = listOf(remoteArticle("g")))
            val feeds = feedRepo(db, fetcher)
            feeds.addFeed("https://a.example.com/feed.xml")
            feeds.addFeed("https://b.example.com/feed.xml")
            fetcher.failUrls =
                setOf("https://a.example.com/feed.xml", "https://b.example.com/feed.xml")

            feeds.refreshAllFeeds().shouldBeFailure()
        }

        test("refreshAllFeeds succeeds when there are no feeds") {
            val db = newDatabase()
            val feeds = feedRepo(db, FakeFeedFetcher())

            feeds.refreshAllFeeds().isSuccess shouldBe true
        }
    })

private class FakeFeedFetcher(
    var articles: List<RemoteArticle> = emptyList(),
    var failUrls: Set<String> = emptySet(),
) : FeedFetcher {
    override suspend fun fetch(url: String): RemoteFeed {
        check(url !in failUrls) { "fetch failed for $url" }
        return RemoteFeed(title = "Title", siteUrl = null, description = null, articles = articles)
    }
}

private fun remoteArticle(
    guid: String,
    title: String = "Title $guid",
    publishedAtMs: Long? = 1_000,
): RemoteArticle =
    RemoteArticle(
        guid = guid,
        title = title,
        url = "https://example.com/$guid",
        author = null,
        summary = null,
        content = null,
        publishedAt = publishedAtMs?.let { Instant.fromEpochMilliseconds(it) },
    )
