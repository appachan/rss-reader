package com.github.appachan.rssreader.data.fake

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private const val FEED_LIST_FETCH_DELAY_MS = 600L
private const val ARTICLE_FETCH_BASE_DELAY_MS = 400L
private const val ARTICLE_FETCH_STEP_DELAY_MS = 350L

/**
 * Shared in-memory store standing in for the real Ktor/SQLDelight data layer (a separate task). It
 * is wired in `:app` only, so `:feature:feed` stays free of any data-layer dependency (ADR-0006).
 *
 * [refreshAll] reproduces the dependency-ordered parallel fetch the feed-list screen exercises: the
 * feed list is published first, then each feed's latest article is fetched in parallel with a
 * staggered delay so rows fill in one after another.
 */
class FakeRssStore {
    val feeds = MutableStateFlow<List<Feed>>(emptyList())
    val articlesByFeed = MutableStateFlow<Map<Long, List<Article>>>(emptyMap())

    /** Flip to make the next [refreshAll] fail, to exercise the error path manually. */
    var nextRefreshFails: Boolean = false

    suspend fun refreshAll(): Result<Unit> {
        if (nextRefreshFails) {
            nextRefreshFails = false
            return Result.failure(IllegalStateException("ネットワークに接続できません"))
        }
        delay(FEED_LIST_FETCH_DELAY_MS)
        val now = Clock.System.now()
        val sample = sampleFeeds(now)
        feeds.value = sample
        coroutineScope {
            sample.forEachIndexed { index, feed ->
                launch {
                    delay(ARTICLE_FETCH_BASE_DELAY_MS + index * ARTICLE_FETCH_STEP_DELAY_MS)
                    articlesByFeed.update { it + (feed.id.value to sampleArticles(feed, now)) }
                }
            }
        }
        return Result.success(Unit)
    }
}

/** Commands outside the feed-list scope (#7) are owned by later tasks and left unsupported. */
class FakeFeedRepository(private val store: FakeRssStore) : FeedRepository {
    override fun observeFeeds(): Flow<List<Feed>> = store.feeds.asStateFlow()

    override fun observeFeed(id: FeedId): Flow<Feed?> =
        store.feeds.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refreshAllFeeds(): Result<Unit> = store.refreshAll()

    override suspend fun refreshFeed(id: FeedId): Result<Unit> = store.refreshAll()

    override suspend fun addFeed(url: String): Result<FeedId> =
        Result.failure(unsupported("addFeed", taskRef = "#12"))

    override suspend fun removeFeed(id: FeedId): Result<Unit> =
        Result.failure(unsupported("removeFeed", taskRef = "#12"))
}

class FakeArticleRepository(private val store: FakeRssStore) : ArticleRepository {
    override fun observeArticles(feedId: FeedId): Flow<List<Article>> =
        store.articlesByFeed.map { it[feedId.value].orEmpty() }

    override fun observeAllArticles(): Flow<List<Article>> =
        store.articlesByFeed.map { it.values.flatten() }

    override fun observeArticle(id: ArticleId): Flow<Article?> =
        store.articlesByFeed.map { byFeed -> byFeed.values.flatten().firstOrNull { it.id == id } }

    override suspend fun markRead(id: ArticleId): Result<Unit> =
        Result.failure(unsupported("markRead", taskRef = "#14"))

    override suspend fun markUnread(id: ArticleId): Result<Unit> =
        Result.failure(unsupported("markUnread", taskRef = "#14"))
}

private fun unsupported(name: String, taskRef: String): Throwable =
    UnsupportedOperationException(
        "$name is out of scope for the feed-list fake (owned by $taskRef)"
    )

private fun sampleFeeds(now: Instant): List<Feed> =
    listOf(
            "Android Developers Blog" to "https://android-developers.googleblog.com/",
            "Kotlin Blog" to "https://blog.jetbrains.com/kotlin/",
            "Jetpack Compose Releases" to "https://developer.android.com/jetpack/compose",
            "Square Engineering" to "https://developer.squareup.com/blog/",
        )
        .mapIndexed { index, (title, site) ->
            Feed(
                id = FeedId(index + 1L),
                url = "$site/feed.xml",
                title = title,
                siteUrl = site,
                description = "$title のフィード",
                lastFetchedAt = now,
            )
        }

private fun sampleArticles(feed: Feed, now: Instant): List<Article> =
    listOf(
        Article(
            id = ArticleId(feed.id.value),
            feedId = feed.id,
            guid = "${feed.id.value}-latest",
            title = "${feed.title} の最新記事",
            url = "${feed.siteUrl}/latest",
            author = null,
            summary = null,
            content = null,
            publishedAt = now,
            isRead = false,
        )
    )
