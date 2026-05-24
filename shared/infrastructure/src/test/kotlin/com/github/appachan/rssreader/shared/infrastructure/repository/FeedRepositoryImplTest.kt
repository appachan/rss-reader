package com.github.appachan.rssreader.shared.infrastructure.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.appachan.rssreader.shared.infrastructure.db.FeedDatabase
import com.github.appachan.rssreader.shared.infrastructure.remote.FeedFetcher
import com.github.appachan.rssreader.shared.infrastructure.remote.RemoteFeed
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Pure-JVM tests for the repository's persistence and validation handling, with an in-memory
 * SQLDelight driver and a fake [FeedFetcher]. The real fetch+parse path (RssParserFeedFetcher)
 * depends on the rssparser Android variant, so it is exercised on-device, not here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedRepositoryImplTest :
    FunSpec({
        fun newDatabase(): FeedDatabase {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            FeedDatabase.Schema.create(driver)
            return FeedDatabase(driver)
        }

        fun repository(db: FeedDatabase, fetcher: FeedFetcher) =
            FeedRepositoryImpl(db, fetcher, UnconfinedTestDispatcher())

        test("addFeed persists fetched metadata and returns the new id") {
            val db = newDatabase()
            val repo =
                repository(
                    db,
                    fetcherReturning(RemoteFeed("Example", "https://example.com", "desc")),
                )

            repo.addFeed("https://example.com/feed.xml").isSuccess shouldBe true

            val rows = db.feedQueries.selectAll().executeAsList()
            rows shouldHaveSize 1
            rows[0].url shouldBe "https://example.com/feed.xml"
            rows[0].title shouldBe "Example"
            rows[0].site_url shouldBe "https://example.com"
            rows[0].last_fetched_at.shouldNotBeNull()
        }

        test("addFeed falls back to the URL as title when the feed has none") {
            val db = newDatabase()
            val repo =
                repository(
                    db,
                    fetcherReturning(RemoteFeed(title = null, siteUrl = null, description = null)),
                )

            repo.addFeed("https://example.com/feed.xml")

            db.feedQueries.selectAll().executeAsOne().title shouldBe "https://example.com/feed.xml"
        }

        test("addFeed fails and persists nothing when the fetch throws") {
            val db = newDatabase()
            val repo = repository(db, fetcherThrowing())

            repo.addFeed("https://example.com/feed.xml").shouldBeFailure()

            db.feedQueries.selectAll().executeAsList() shouldHaveSize 0
        }

        test("addFeed rejects a duplicate URL") {
            val db = newDatabase()
            val repo = repository(db, fetcherReturning(RemoteFeed("Example", null, null)))
            repo.addFeed("https://example.com/feed.xml")

            repo.addFeed("https://example.com/feed.xml").shouldBeFailure()

            db.feedQueries.selectAll().executeAsList() shouldHaveSize 1
        }

        test("removeFeed deletes the row") {
            val db = newDatabase()
            val repo = repository(db, fetcherReturning(RemoteFeed("Example", null, null)))
            val id = repo.addFeed("https://example.com/feed.xml").getOrThrow()

            repo.removeFeed(id).isSuccess shouldBe true

            db.feedQueries.selectAll().executeAsList() shouldHaveSize 0
        }

        test("observeFeeds maps stored rows to the domain model") {
            val db = newDatabase()
            val repo =
                repository(
                    db,
                    fetcherReturning(RemoteFeed("Example", "https://example.com", "desc")),
                )
            repo.addFeed("https://example.com/feed.xml")

            val feeds = repo.observeFeeds().first()

            feeds shouldHaveSize 1
            feeds[0].url shouldBe "https://example.com/feed.xml"
            feeds[0].title shouldBe "Example"
            feeds[0].siteUrl shouldBe "https://example.com"
            feeds[0].lastFetchedAt.shouldNotBeNull()
        }
    })

private fun fetcherReturning(remote: RemoteFeed): FeedFetcher =
    object : FeedFetcher {
        override suspend fun fetch(url: String): RemoteFeed = remote
    }

private fun fetcherThrowing(): FeedFetcher =
    object : FeedFetcher {
        override suspend fun fetch(url: String): RemoteFeed = error("network down")
    }
