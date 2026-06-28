package com.github.appachan.rssreader.feature.feed.reducer

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.testing.FakeFeedDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

private const val FEED_LIST_DELAY = 600L
private const val ARTICLE_BASE_DELAY = 400L
private const val ARTICLE_STEP_DELAY = 350L

/**
 * End-to-end coverage of the pattern ③ ViewModel: it dispatches actions into [reduce] off the back
 * of the repository's coroutines, so this drives it with a self-contained [StandardTestDispatcher]
 * installed as Main and asserts `state.value` at controlled scheduler steps. The pure transitions
 * themselves are covered separately and far more cheaply in `FeedReducerTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReducerFeedListViewModelTest :
    FunSpec({
        // This spec drives its own StandardTestDispatcher as Main (below), so it opts out of the
        // project-wide coroutineTestScope: it doesn't use Kotest's injected scope, and leaving the
        // auto-installed Main on would have two specs in this module mutating the global Main
        // concurrently under Kotest parallelism. Both specs here own their dispatcher and opt out.
        coroutineTestScope = false

        afterTest { Dispatchers.resetMain() }

        test("初期ロード: loading → feeds 取得で各行 loading → 各行が順次埋まる") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
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

            val viewModel = ReducerFeedListViewModel(fake, fake)

            // Nothing has run yet: still the default state (load() has no constructor side effect).
            viewModel.state.value shouldBe ReducerFeedState()

            // load() launches: isLoading flips on, feed list not fetched yet.
            viewModel.load()
            scheduler.runCurrent()
            viewModel.state.value.isLoading shouldBe true
            viewModel.state.value.items shouldHaveSize 0

            // Feed list arrives; every row is shown loading its latest article.
            scheduler.advanceTimeBy(FEED_LIST_DELAY)
            scheduler.runCurrent()
            with(viewModel.state.value) {
                isLoading shouldBe true
                items shouldHaveSize 2
                items.forAll { it.isArticleLoading shouldBe true }
            }

            // Only the first feed's article has resolved → rows fill independently (順次表示).
            scheduler.advanceTimeBy(ARTICLE_BASE_DELAY)
            scheduler.runCurrent()
            with(viewModel.state.value) {
                items[0].isArticleLoading shouldBe false
                items[0].latestArticleTitle shouldBe "Article 1"
                items[1].isArticleLoading shouldBe true
            }

            // Everything settled.
            scheduler.advanceUntilIdle()
            with(viewModel.state.value) {
                isLoading shouldBe false
                isRefreshing shouldBe false
                items.forAll { it.isArticleLoading shouldBe false }
                items[1].latestArticleTitle shouldBe "Article 2"
            }
        }

        test("pull-to-refresh: 既存データを保持しつつ isRefreshing を立てる") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val feeds = listOf(feed(1))
            val fake =
                FakeFeedDataSource(
                    feeds = feeds,
                    articles = mapOf(FeedId(1) to listOf(article(1))),
                    feedListDelayMs = FEED_LIST_DELAY,
                    articleBaseDelayMs = ARTICLE_BASE_DELAY,
                )
            val viewModel = ReducerFeedListViewModel(fake, fake)
            viewModel.load()
            scheduler.advanceUntilIdle()
            viewModel.state.value.items shouldHaveSize 1

            viewModel.refresh()
            scheduler.runCurrent()
            with(viewModel.state.value) {
                isRefreshing shouldBe true
                isLoading shouldBe false
                items shouldHaveSize 1
                items[0].latestArticleTitle shouldBe "Article 1"
                items[0].isArticleLoading shouldBe false
            }

            scheduler.advanceUntilIdle()
            viewModel.state.value.isRefreshing shouldBe false
            viewModel.state.value.items shouldHaveSize 1
        }

        test("エラー: refresh 失敗で error をセットし、retry で復帰する") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake =
                FakeFeedDataSource(
                        feeds = listOf(feed(1)),
                        articles = mapOf(FeedId(1) to listOf(article(1))),
                        feedListDelayMs = FEED_LIST_DELAY,
                    )
                    .apply { failRefresh = true }
            val viewModel = ReducerFeedListViewModel(fake, fake)
            viewModel.load()

            scheduler.advanceUntilIdle()
            with(viewModel.state.value) {
                isLoading shouldBe false
                isError shouldBe true
                items shouldHaveSize 0
            }

            fake.failRefresh = false
            viewModel.retry()
            scheduler.advanceUntilIdle()
            with(viewModel.state.value) {
                isError shouldBe false
                items shouldHaveSize 1
            }
        }

        test(
            "initial load: a feed with no articles resolves to not-loading with no article title"
        ) {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake =
                FakeFeedDataSource(
                    feeds = listOf(feed(1)),
                    articles = emptyMap(),
                    feedListDelayMs = FEED_LIST_DELAY,
                )
            val viewModel = ReducerFeedListViewModel(fake, fake)
            viewModel.load()

            scheduler.advanceUntilIdle()
            with(viewModel.state.value) {
                items shouldHaveSize 1
                items[0].isArticleLoading shouldBe false
                items[0].latestArticleTitle shouldBe null
            }
        }

        test("submitAddFeed: a malformed URL is rejected locally without calling the repository") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake = FakeFeedDataSource(feeds = emptyList())
            val viewModel = ReducerFeedListViewModel(fake, fake)

            viewModel.showAddSheet()
            viewModel.onAddUrlChange("not-a-url")
            viewModel.submitAddFeed()
            scheduler.advanceUntilIdle()

            with(viewModel.state.value) {
                addFeedError shouldBe AddFeedError.INVALID_URL
                isAddingFeed shouldBe false
                isAddSheetVisible shouldBe true
            }
            fake.addedUrls shouldHaveSize 0
        }

        test("submitAddFeed: success closes the dialog and clears the input") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake = FakeFeedDataSource(feeds = emptyList())
            val viewModel = ReducerFeedListViewModel(fake, fake)

            viewModel.showAddSheet()
            viewModel.onAddUrlChange("https://example.com/feed.xml")
            viewModel.submitAddFeed()
            scheduler.advanceUntilIdle()

            with(viewModel.state.value) {
                isAddingFeed shouldBe false
                isAddSheetVisible shouldBe false
                addUrlInput shouldBe ""
                addFeedError shouldBe null
            }
            fake.addedUrls shouldBe listOf("https://example.com/feed.xml")
        }

        test("submitAddFeed: failure shows COULD_NOT_ADD and keeps the dialog open") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake = FakeFeedDataSource(feeds = emptyList()).apply { failAddFeed = true }
            val viewModel = ReducerFeedListViewModel(fake, fake)

            viewModel.showAddSheet()
            viewModel.onAddUrlChange("https://example.com/feed.xml")
            viewModel.submitAddFeed()
            scheduler.advanceUntilIdle()

            with(viewModel.state.value) {
                isAddingFeed shouldBe false
                addFeedError shouldBe AddFeedError.COULD_NOT_ADD
                isAddSheetVisible shouldBe true
            }
        }

        test("submitAddFeed: a non-https URL is rejected locally") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val fake = FakeFeedDataSource(feeds = emptyList())
            val viewModel = ReducerFeedListViewModel(fake, fake)

            viewModel.showAddSheet()
            viewModel.onAddUrlChange("http://example.com/feed.xml")
            viewModel.submitAddFeed()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.state.value.addFeedError shouldBe AddFeedError.INVALID_URL
            fake.addedUrls shouldHaveSize 0
        }

        test("submitAddFeed: ignores a second submit while one is in flight") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val fake = FakeFeedDataSource(feeds = emptyList())
            val viewModel = ReducerFeedListViewModel(fake, fake)

            viewModel.showAddSheet()
            viewModel.onAddUrlChange("https://example.com/feed.xml")
            viewModel.submitAddFeed()
            viewModel.submitAddFeed()
            dispatcher.scheduler.advanceUntilIdle()

            fake.addedUrls shouldHaveSize 1
        }

        test("confirmDeleteFeed: deletes via the repository and clears the pending state") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake = FakeFeedDataSource(feeds = listOf(feed(1)))
            val viewModel = ReducerFeedListViewModel(fake, fake)

            viewModel.requestDeleteFeed(FeedId(1))
            viewModel.state.value.pendingDeletion shouldBe FeedId(1)

            viewModel.confirmDeleteFeed()
            scheduler.advanceUntilIdle()

            fake.removedIds shouldBe listOf(FeedId(1))
            viewModel.state.value.pendingDeletion shouldBe null
        }

        test("confirmDeleteFeed: failure emits DeleteFailed and clears the pending state") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake = FakeFeedDataSource(feeds = listOf(feed(1))).apply { failRemoveFeed = true }
            val viewModel = ReducerFeedListViewModel(fake, fake)

            val events = mutableListOf<ReducerFeedEvent>()
            val collector =
                CoroutineScope(dispatcher).launch { viewModel.events.collect { events += it } }
            scheduler.runCurrent()

            viewModel.requestDeleteFeed(FeedId(1))
            viewModel.confirmDeleteFeed()
            scheduler.advanceUntilIdle()

            events shouldBe listOf(ReducerFeedEvent.DeleteFailed)
            viewModel.state.value.pendingDeletion shouldBe null
            collector.cancel()
        }
    })

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
