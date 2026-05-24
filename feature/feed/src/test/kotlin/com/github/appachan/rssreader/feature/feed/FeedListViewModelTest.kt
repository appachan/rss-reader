package com.github.appachan.rssreader.feature.feed

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

private const val FEED_LIST_DELAY = 600L
private const val ARTICLE_BASE_DELAY = 400L
private const val ARTICLE_STEP_DELAY = 350L

/**
 * Drives the ViewModel with a self-contained [StandardTestDispatcher] installed as Main, so
 * `viewModelScope` and the fake's `delay`s share one virtual clock. State is asserted by reading
 * `uiState.value` at controlled scheduler steps — deterministic for the progressive-fill sequence
 * this baseline must verify.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedListViewModelTest :
    FunSpec({
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

            val viewModel = FeedListViewModel(fake, fake)

            // Nothing has run yet: still the default state.
            viewModel.uiState.value shouldBe FeedListUiState()

            // load() launches: isLoading flips on, feed list not fetched yet.
            scheduler.runCurrent()
            viewModel.uiState.value.isLoading shouldBe true
            viewModel.uiState.value.items shouldHaveSize 0

            // Feed list arrives; every row is shown loading its latest article.
            scheduler.advanceTimeBy(FEED_LIST_DELAY)
            scheduler.runCurrent()
            with(viewModel.uiState.value) {
                isLoading shouldBe true
                items shouldHaveSize 2
                items.forAll { it.isArticleLoading shouldBe true }
            }

            // Only the first feed's article has resolved → rows fill independently (順次表示).
            scheduler.advanceTimeBy(ARTICLE_BASE_DELAY)
            scheduler.runCurrent()
            with(viewModel.uiState.value) {
                items[0].isArticleLoading shouldBe false
                items[0].latestArticleTitle shouldBe "Article 1"
                items[1].isArticleLoading shouldBe true
            }

            // Everything settled.
            scheduler.advanceUntilIdle()
            with(viewModel.uiState.value) {
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
            val viewModel = FeedListViewModel(fake, fake)
            scheduler.advanceUntilIdle()
            viewModel.uiState.value.items shouldHaveSize 1

            viewModel.refresh()
            scheduler.runCurrent()
            with(viewModel.uiState.value) {
                isRefreshing shouldBe true
                isLoading shouldBe false
                items shouldHaveSize 1
                items[0].latestArticleTitle shouldBe "Article 1"
                items[0].isArticleLoading shouldBe false
            }

            scheduler.advanceUntilIdle()
            viewModel.uiState.value.isRefreshing shouldBe false
            viewModel.uiState.value.items shouldHaveSize 1
        }

        test("エラー: refresh 失敗で error をセットし、retry で復帰する") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake =
                FakeFeedDataSource(
                    feeds = listOf(feed(1)),
                    articles = mapOf(FeedId(1) to listOf(article(1))),
                    failRefresh = true,
                    feedListDelayMs = FEED_LIST_DELAY,
                )
            val viewModel = FeedListViewModel(fake, fake)

            scheduler.advanceUntilIdle()
            with(viewModel.uiState.value) {
                isLoading shouldBe false
                error.shouldNotBeNull()
                items shouldHaveSize 0
            }

            fake.failRefresh = false
            viewModel.retry()
            scheduler.advanceUntilIdle()
            with(viewModel.uiState.value) {
                error shouldBe null
                items shouldHaveSize 1
            }
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
