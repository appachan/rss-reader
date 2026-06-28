package com.github.appachan.rssreader.feature.feed.orbit

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.testing.FakeFeedDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.orbitmvi.orbit.test.Item
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test

private const val FEED_LIST_DELAY = 600L
private const val ARTICLE_BASE_DELAY = 400L
private const val ARTICLE_STEP_DELAY = 350L

/**
 * Pattern ④ (Orbit MVI) port of the pattern ① ViewModel tests — same acceptance conditions,
 * exercised through Orbit's own test harness ([test]). The harness swaps the container onto the
 * [runTest] scheduler so the fake's `delay`s share one virtual clock, then surfaces every emitted
 * state and side effect through a Turbine; [awaitStateMatching] / [awaitSideEffect] drive over the
 * intermediate emissions to the milestones each case asserts.
 *
 * `runOnCreate()` triggers the container's `onCreate` (the equivalent of pattern ①'s `load()`),
 * which the test harness does not run automatically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrbitFeedListViewModelTest :
    FunSpec({
        // The ViewModel touches viewModelScope when its container is built, so Main must resolve.
        // The real work runs on the Orbit test scheduler, not this dispatcher.
        beforeTest { Dispatchers.setMain(UnconfinedTestDispatcher()) }
        afterTest { Dispatchers.resetMain() }

        test("初期ロード: loading → feeds 取得で各行 loading → 各行が順次埋まる") {
            val feeds = listOf(feed(1), feed(2))
            val fake =
                FakeFeedDataSource(
                    feeds = feeds,
                    articles =
                        mapOf(FeedId(1) to listOf(article(1)), FeedId(2) to listOf(article(2))),
                    feedListDelayMs = FEED_LIST_DELAY,
                    articleBaseDelayMs = ARTICLE_BASE_DELAY,
                    articleStepDelayMs = ARTICLE_STEP_DELAY,
                )

            runTest {
                OrbitFeedListViewModel(fake, fake).test(this) {
                    runOnCreate()

                    // Feed list arrives; every row is shown loading its latest article.
                    awaitStateMatching {
                            it.items.size == 2 && it.items.all { row -> row.isArticleLoading }
                        }
                        .let { it.isLoading shouldBe true }

                    // First feed's article resolves first → rows fill independently (順次表示).
                    awaitStateMatching { it.items[0].isArticleLoading.not() }
                        .let {
                            it.items[0].latestArticleTitle shouldBe "Article 1"
                            it.items[1].isArticleLoading shouldBe true
                        }

                    // Everything settled.
                    awaitStateMatching {
                            settled(it) && it.items.all { row -> !row.isArticleLoading }
                        }
                        .let {
                            it.items shouldHaveSize 2
                            it.items[1].latestArticleTitle shouldBe "Article 2"
                        }

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test("pull-to-refresh: 既存データを保持しつつ isRefreshing を立てる") {
            val fake =
                FakeFeedDataSource(
                    feeds = listOf(feed(1)),
                    articles = mapOf(FeedId(1) to listOf(article(1))),
                    feedListDelayMs = FEED_LIST_DELAY,
                    articleBaseDelayMs = ARTICLE_BASE_DELAY,
                )

            runTest {
                val viewModel = OrbitFeedListViewModel(fake, fake)
                viewModel.test(this) {
                    runOnCreate()
                    awaitStateMatching {
                        settled(it) && it.items.size == 1 && !it.items[0].isArticleLoading
                    }

                    viewModel.refresh()
                    awaitStateMatching { it.isRefreshing }
                        .let {
                            it.isLoading shouldBe false
                            it.items shouldHaveSize 1
                            it.items[0].latestArticleTitle shouldBe "Article 1"
                            it.items[0].isArticleLoading shouldBe false
                        }

                    awaitStateMatching { settled(it) }.items shouldHaveSize 1

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test("エラー: refresh 失敗で error をセットし、retry で復帰する") {
            val fake =
                FakeFeedDataSource(
                        feeds = listOf(feed(1)),
                        articles = mapOf(FeedId(1) to listOf(article(1))),
                        feedListDelayMs = FEED_LIST_DELAY,
                    )
                    .apply { failRefresh = true }

            runTest {
                val viewModel = OrbitFeedListViewModel(fake, fake)
                viewModel.test(this) {
                    runOnCreate()
                    awaitStateMatching { it.isError }
                        .let {
                            it.isLoading shouldBe false
                            it.items shouldHaveSize 0
                        }

                    fake.failRefresh = false
                    viewModel.retry()
                    awaitStateMatching { !it.isError && it.items.isNotEmpty() }.items shouldHaveSize
                        1

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test(
            "initial load: a feed with no articles resolves to not-loading with no article title"
        ) {
            val fake =
                FakeFeedDataSource(
                    feeds = listOf(feed(1)),
                    articles = emptyMap(),
                    feedListDelayMs = FEED_LIST_DELAY,
                )

            runTest {
                OrbitFeedListViewModel(fake, fake).test(this) {
                    runOnCreate()
                    awaitStateMatching {
                            settled(it) && it.items.size == 1 && !it.items[0].isArticleLoading
                        }
                        .let { it.items[0].latestArticleTitle shouldBe null }

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test("submitAddFeed: a malformed URL is rejected locally without calling the repository") {
            val fake = FakeFeedDataSource(feeds = emptyList())

            runTest {
                val viewModel = OrbitFeedListViewModel(fake, fake)
                viewModel.test(this) {
                    viewModel.showAddSheet()
                    viewModel.onAddUrlChange("not-a-url")
                    viewModel.submitAddFeed()

                    awaitStateMatching { it.addFeedError == AddFeedError.INVALID_URL }
                        .let {
                            it.isAddingFeed shouldBe false
                            it.isAddSheetVisible shouldBe true
                        }
                    fake.addedUrls shouldHaveSize 0

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test("submitAddFeed: success closes the dialog and clears the input") {
            val fake = FakeFeedDataSource(feeds = emptyList())

            runTest {
                val viewModel = OrbitFeedListViewModel(fake, fake)
                viewModel.test(this) {
                    viewModel.showAddSheet()
                    viewModel.onAddUrlChange("https://example.com/feed.xml")
                    viewModel.submitAddFeed()

                    awaitStateMatching { !it.isAddSheetVisible && !it.isAddingFeed }
                        .let {
                            it.addUrlInput shouldBe ""
                            it.addFeedError shouldBe null
                        }
                    fake.addedUrls shouldBe listOf("https://example.com/feed.xml")

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test("submitAddFeed: failure shows COULD_NOT_ADD and keeps the dialog open") {
            val fake = FakeFeedDataSource(feeds = emptyList()).apply { failAddFeed = true }

            runTest {
                val viewModel = OrbitFeedListViewModel(fake, fake)
                viewModel.test(this) {
                    viewModel.showAddSheet()
                    viewModel.onAddUrlChange("https://example.com/feed.xml")
                    viewModel.submitAddFeed()

                    awaitStateMatching { it.addFeedError == AddFeedError.COULD_NOT_ADD }
                        .let {
                            it.isAddingFeed shouldBe false
                            it.isAddSheetVisible shouldBe true
                        }

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test("submitAddFeed: a non-https URL is rejected locally") {
            val fake = FakeFeedDataSource(feeds = emptyList())

            runTest {
                val viewModel = OrbitFeedListViewModel(fake, fake)
                viewModel.test(this) {
                    viewModel.showAddSheet()
                    viewModel.onAddUrlChange("http://example.com/feed.xml")
                    viewModel.submitAddFeed()

                    awaitStateMatching { it.addFeedError == AddFeedError.INVALID_URL }
                    fake.addedUrls shouldHaveSize 0

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test("submitAddFeed: ignores a second submit while one is in flight") {
            val fake = FakeFeedDataSource(feeds = emptyList())

            runTest {
                val viewModel = OrbitFeedListViewModel(fake, fake)
                viewModel.test(this) {
                    viewModel.showAddSheet()
                    viewModel.onAddUrlChange("https://example.com/feed.xml")
                    viewModel.submitAddFeed()
                    viewModel.submitAddFeed()

                    awaitStateMatching { !it.isAddSheetVisible && !it.isAddingFeed }
                    fake.addedUrls shouldHaveSize 1

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test("confirmDeleteFeed: deletes via the repository and clears the pending state") {
            val fake = FakeFeedDataSource(feeds = listOf(feed(1)))

            runTest {
                val viewModel = OrbitFeedListViewModel(fake, fake)
                viewModel.test(this) {
                    viewModel.requestDeleteFeed(FeedId(1))
                    awaitStateMatching { it.pendingDeletion == FeedId(1) }

                    viewModel.confirmDeleteFeed()
                    awaitStateMatching { it.pendingDeletion == null }

                    fake.removedIds shouldBe listOf(FeedId(1))

                    cancelAndIgnoreRemainingItems()
                }
            }
        }

        test("confirmDeleteFeed: failure emits DeleteFailed and clears the pending state") {
            val fake = FakeFeedDataSource(feeds = listOf(feed(1))).apply { failRemoveFeed = true }

            runTest {
                val viewModel = OrbitFeedListViewModel(fake, fake)
                viewModel.test(this) {
                    viewModel.requestDeleteFeed(FeedId(1))
                    awaitStateMatching { it.pendingDeletion == FeedId(1) }

                    viewModel.confirmDeleteFeed()
                    awaitStateMatching { it.pendingDeletion == null }
                    awaitSideEffect() shouldBe FeedListSideEffect.DeleteFailed

                    fake.removedIds shouldBe listOf(FeedId(1))

                    cancelAndIgnoreRemainingItems()
                }
            }
        }
    })

/** A fully-loaded screen: neither the initial spinner nor a pull-to-refresh is in progress. */
private fun settled(state: OrbitFeedListState): Boolean = !state.isLoading && !state.isRefreshing

/** Consume emitted items until a state satisfies [predicate]; fail on an unexpected side effect. */
private suspend fun OrbitTestContext<OrbitFeedListState, FeedListSideEffect, *>.awaitStateMatching(
    predicate: (OrbitFeedListState) -> Boolean
): OrbitFeedListState {
    while (true) {
        when (val item = awaitItem()) {
            is Item.StateItem -> if (predicate(item.value)) return item.value
            is Item.SideEffectItem -> error("Unexpected side effect: ${item.value}")
        }
    }
}

private fun feed(id: Long): Feed =
    Feed(
        id = FeedId(id),
        url = "https://example.com/feed$id",
        title = "Feed $id",
        siteUrl = null,
        description = null,
        lastFetchedAt = null,
    )

private fun article(feedId: Long): Article =
    Article(
        id = ArticleId(feedId * 10),
        feedId = FeedId(feedId),
        guid = "guid-$feedId",
        title = "Article $feedId",
        url = "https://example.com/article$feedId",
        author = null,
        summary = null,
        content = null,
        publishedAt = Instant.fromEpochMilliseconds(feedId * 1_000),
        isRead = false,
    )
