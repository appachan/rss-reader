package com.github.appachan.rssreader.feature.feed.async

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.testing.FakeFeedDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
 * Pattern ② counterpart of `FeedListViewModelTest`: same requirements and timing, but state is read
 * through the [Async] wrappers. [uiState] is a `stateIn(Eagerly)` `combine`, so its upstream runs
 * as soon as the scheduler advances and `uiState.value` reflects the latest combined state without
 * an explicit collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AsyncFeedListViewModelTest :
    FunSpec({
        afterTest { Dispatchers.resetMain() }

        test("初期ロード: Loading → feeds 取得で各行 Loading → 各行が順次 Success に埋まる") {
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

            val viewModel = AsyncFeedListViewModel(fake, fake)

            // Nothing has run yet: still the default state (load() has no constructor side effect).
            viewModel.uiState.value shouldBe AsyncFeedListUiState()

            // load() launches: feeds flips to Loading, feed list not fetched yet.
            viewModel.load()
            scheduler.runCurrent()
            viewModel.uiState.value.feeds shouldBe Async.Loading

            // Feed list arrives; every row is shown loading its latest article.
            scheduler.advanceTimeBy(FEED_LIST_DELAY)
            scheduler.runCurrent()
            with(viewModel.uiState.value.successData()) {
                this shouldHaveSize 2
                forAll { it.latestArticle shouldBe Async.Loading }
            }

            // Only the first feed's article has resolved → rows fill independently (順次表示).
            scheduler.advanceTimeBy(ARTICLE_BASE_DELAY)
            scheduler.runCurrent()
            with(viewModel.uiState.value.successData()) {
                this[0].latestArticle shouldBe Async.Success(LatestArticle("Article 1", instant(1)))
                this[1].latestArticle shouldBe Async.Loading
            }

            // Everything settled.
            scheduler.advanceUntilIdle()
            viewModel.uiState.value.isRefreshing shouldBe false
            with(viewModel.uiState.value.successData()) {
                forAll { it.latestArticle.shouldBeInstanceOf<Async.Success<*>>() }
                this[1].latestArticle shouldBe Async.Success(LatestArticle("Article 2", instant(2)))
            }
        }

        test("pull-to-refresh: 既存データを保持しつつ isRefreshing を立てる") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake =
                FakeFeedDataSource(
                    feeds = listOf(feed(1)),
                    articles = mapOf(FeedId(1) to listOf(article(1))),
                    feedListDelayMs = FEED_LIST_DELAY,
                    articleBaseDelayMs = ARTICLE_BASE_DELAY,
                )
            val viewModel = AsyncFeedListViewModel(fake, fake)
            viewModel.load()
            scheduler.advanceUntilIdle()
            viewModel.uiState.value.successData() shouldHaveSize 1

            viewModel.refresh()
            scheduler.runCurrent()
            with(viewModel.uiState.value) {
                isRefreshing shouldBe true
                with(successData()) {
                    this shouldHaveSize 1
                    this[0].latestArticle shouldBe
                        Async.Success(LatestArticle("Article 1", instant(1)))
                }
            }

            scheduler.advanceUntilIdle()
            viewModel.uiState.value.isRefreshing shouldBe false
            viewModel.uiState.value.successData() shouldHaveSize 1
        }

        test("エラー: refresh 失敗で feeds=Failure をセットし、retry で復帰する") {
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
            val viewModel = AsyncFeedListViewModel(fake, fake)
            viewModel.load()

            scheduler.advanceUntilIdle()
            with(viewModel.uiState.value) {
                isRefreshing shouldBe false
                feeds.shouldBeInstanceOf<Async.Failure>()
            }

            fake.failRefresh = false
            viewModel.retry()
            scheduler.advanceUntilIdle()
            viewModel.uiState.value.successData() shouldHaveSize 1
        }

        test("initial load: a feed with no articles resolves to Success(null)") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake =
                FakeFeedDataSource(
                    feeds = listOf(feed(1)),
                    articles = emptyMap(),
                    feedListDelayMs = FEED_LIST_DELAY,
                )
            val viewModel = AsyncFeedListViewModel(fake, fake)
            viewModel.load()

            scheduler.advanceUntilIdle()
            with(viewModel.uiState.value.successData()) {
                this shouldHaveSize 1
                this[0].latestArticle shouldBe Async.Success(null)
            }
        }

        test("submitAddFeed: a malformed URL is rejected locally without calling the repository") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake = FakeFeedDataSource(feeds = emptyList())
            val viewModel = AsyncFeedListViewModel(fake, fake)

            viewModel.showAddSheet()
            viewModel.onAddUrlChange("not-a-url")
            viewModel.submitAddFeed()
            scheduler.advanceUntilIdle()

            with(viewModel.uiState.value) {
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
            val viewModel = AsyncFeedListViewModel(fake, fake)

            viewModel.showAddSheet()
            viewModel.onAddUrlChange("https://example.com/feed.xml")
            viewModel.submitAddFeed()
            scheduler.advanceUntilIdle()

            with(viewModel.uiState.value) {
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
            val viewModel = AsyncFeedListViewModel(fake, fake)

            viewModel.showAddSheet()
            viewModel.onAddUrlChange("https://example.com/feed.xml")
            viewModel.submitAddFeed()
            scheduler.advanceUntilIdle()

            with(viewModel.uiState.value) {
                isAddingFeed shouldBe false
                addFeedError shouldBe AddFeedError.COULD_NOT_ADD
                isAddSheetVisible shouldBe true
            }
        }

        test("submitAddFeed: a non-https URL is rejected locally") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val fake = FakeFeedDataSource(feeds = emptyList())
            val viewModel = AsyncFeedListViewModel(fake, fake)

            viewModel.showAddSheet()
            viewModel.onAddUrlChange("http://example.com/feed.xml")
            viewModel.submitAddFeed()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.addFeedError shouldBe AddFeedError.INVALID_URL
            fake.addedUrls shouldHaveSize 0
        }

        test("submitAddFeed: ignores a second submit while one is in flight") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val fake = FakeFeedDataSource(feeds = emptyList())
            val viewModel = AsyncFeedListViewModel(fake, fake)

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
            val viewModel = AsyncFeedListViewModel(fake, fake)

            viewModel.requestDeleteFeed(FeedId(1))
            // uiState is a derived combine, so it reflects the update only after the scheduler
            // ticks.
            scheduler.runCurrent()
            viewModel.uiState.value.pendingDeletion shouldBe FeedId(1)

            viewModel.confirmDeleteFeed()
            scheduler.advanceUntilIdle()

            fake.removedIds shouldBe listOf(FeedId(1))
            viewModel.uiState.value.pendingDeletion shouldBe null
        }

        test("confirmDeleteFeed: failure emits DeleteFailed and clears the pending state") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake = FakeFeedDataSource(feeds = listOf(feed(1))).apply { failRemoveFeed = true }
            val viewModel = AsyncFeedListViewModel(fake, fake)

            val events = mutableListOf<FeedListEvent>()
            val collector =
                CoroutineScope(dispatcher).launch { viewModel.events.collect { events += it } }
            scheduler.runCurrent()

            viewModel.requestDeleteFeed(FeedId(1))
            viewModel.confirmDeleteFeed()
            scheduler.advanceUntilIdle()

            events shouldBe listOf(FeedListEvent.DeleteFailed)
            viewModel.uiState.value.pendingDeletion shouldBe null
            collector.cancel()
        }
    })

private fun AsyncFeedListUiState.successData(): List<FeedRowUiModel> =
    feeds.shouldBeInstanceOf<Async.Success<List<FeedRowUiModel>>>().data

private fun feed(id: Long): Feed =
    Feed(
        id = FeedId(id),
        url = "https://example.com/feed$id",
        title = "Feed $id",
        siteUrl = null,
        description = null,
        lastFetchedAt = null,
    )

private fun instant(feedId: Long): Instant = Instant.fromEpochMilliseconds(feedId * 1_000)

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
        publishedAt = instant(feedId),
        isRead = false,
    )
