package com.github.appachan.rssreader.feature.article

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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant

private const val REFRESH_DELAY = 600L

/**
 * Drives [ArticleListViewModel] against [FakeFeedDataSource] with a [StandardTestDispatcher]
 * installed as Main, so `viewModelScope` and the fake's `delay`s share one virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleListViewModelTest :
    FunSpec({
        afterTest { Dispatchers.resetMain() }

        test("initial load: loading until refresh completes, then articles appear") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake =
                FakeFeedDataSource(
                    feeds = listOf(feed(1)),
                    articles = mapOf(FeedId(1) to listOf(article(1, 11), article(1, 12))),
                    feedListDelayMs = REFRESH_DELAY,
                )
            val viewModel = ArticleListViewModel(fake, fake, FeedId(1))

            viewModel.uiState.value shouldBe ArticleListUiState()

            viewModel.load()
            scheduler.runCurrent()
            viewModel.uiState.value.isLoading shouldBe true
            viewModel.uiState.value.items shouldHaveSize 0

            scheduler.advanceUntilIdle()
            with(viewModel.uiState.value) {
                isLoading shouldBe false
                feedTitle shouldBe "Feed 1"
                items shouldHaveSize 2
            }
        }

        test("pull-to-refresh keeps existing articles visible while refreshing") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake =
                FakeFeedDataSource(
                    feeds = listOf(feed(1)),
                    articles = mapOf(FeedId(1) to listOf(article(1, 11))),
                    feedListDelayMs = REFRESH_DELAY,
                )
            val viewModel = ArticleListViewModel(fake, fake, FeedId(1))
            viewModel.load()
            scheduler.advanceUntilIdle()
            viewModel.uiState.value.items shouldHaveSize 1

            viewModel.refresh()
            scheduler.runCurrent()
            with(viewModel.uiState.value) {
                isRefreshing shouldBe true
                isLoading shouldBe false
                items shouldHaveSize 1
            }

            scheduler.advanceUntilIdle()
            viewModel.uiState.value.isRefreshing shouldBe false
        }

        test("error: refresh failure sets error, retry recovers") {
            val dispatcher = StandardTestDispatcher()
            Dispatchers.setMain(dispatcher)
            val scheduler = dispatcher.scheduler
            val fake =
                FakeFeedDataSource(
                        feeds = listOf(feed(1)),
                        articles = mapOf(FeedId(1) to listOf(article(1, 11))),
                        feedListDelayMs = REFRESH_DELAY,
                    )
                    .apply { failRefresh = true }
            val viewModel = ArticleListViewModel(fake, fake, FeedId(1))
            viewModel.load()

            scheduler.advanceUntilIdle()
            with(viewModel.uiState.value) {
                isLoading shouldBe false
                isError shouldBe true
                items shouldHaveSize 0
            }

            fake.failRefresh = false
            viewModel.retry()
            scheduler.advanceUntilIdle()
            with(viewModel.uiState.value) {
                isError shouldBe false
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

private fun article(feedId: Long, articleId: Long): Article =
    Article(
        id = ArticleId(articleId),
        feedId = FeedId(feedId),
        guid = "guid-$articleId",
        title = "Article $articleId",
        url = "https://example.com/article$articleId",
        author = null,
        summary = "Summary $articleId",
        content = null,
        publishedAt = Instant.fromEpochMilliseconds(articleId * 1_000),
        isRead = false,
    )
