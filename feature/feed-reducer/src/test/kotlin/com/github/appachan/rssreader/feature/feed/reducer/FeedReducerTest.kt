package com.github.appachan.rssreader.feature.feed.reducer

import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.ArticleId
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.model.FeedId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

/**
 * Pattern ③'s payoff: every state transition is a pure `(state, action) -> state` step, so the
 * whole state machine is exercised with plain value assertions — no dispatcher, no coroutines, no
 * fakes. These mirror the behaviours the ViewModel test drives end-to-end, but at the reducer
 * level.
 */
class FeedReducerTest :
    FunSpec({
        // These tests are pure and never launch a coroutine, so opt out of the project-wide
        // coroutineTestScope. Both specs in this module own their dispatcher and opt out (see
        // ReducerFeedListViewModelTest): otherwise the auto-installed Dispatchers.Main would be
        // mutated by two specs concurrently under Kotest parallelism and they'd stomp each other.
        coroutineTestScope = false

        test("LoadStarted: shows the full-screen spinner and clears any prior error") {
            val next = reduce(ReducerFeedState(isError = true), FeedAction.LoadStarted)

            next.isLoading shouldBe true
            next.isError shouldBe false
        }

        test("RefreshStarted: flags refreshing without disturbing existing rows") {
            val state = ReducerFeedState(items = listOf(loadedRow(1)))

            val next = reduce(state, FeedAction.RefreshStarted)

            next.isRefreshing shouldBe true
            next.isLoading shouldBe false
            next.items shouldBe state.items
        }

        test("FeedsObserved: new feeds start loading their latest article") {
            val next =
                reduce(ReducerFeedState(), FeedAction.FeedsObserved(listOf(feed(1), feed(2))))

            next.items shouldHaveSize 2
            next.items.forEach { it.isArticleLoading shouldBe true }
        }

        test("FeedsObserved: preserves an already-resolved article across a refresh") {
            val state = ReducerFeedState(items = listOf(loadedRow(1)))

            val next = reduce(state, FeedAction.FeedsObserved(listOf(feed(1))))

            with(next.items.single()) {
                isArticleLoading shouldBe false
                latestArticleTitle shouldBe "Article 1"
            }
        }

        test("FeedsObserved: a removed feed drops its row") {
            val state = ReducerFeedState(items = listOf(loadedRow(1), loadedRow(2)))

            val next = reduce(state, FeedAction.FeedsObserved(listOf(feed(2))))

            next.items.map { it.feedId } shouldBe listOf(FeedId(2))
        }

        test("ArticleResolved: fills only the matching row") {
            val state = ReducerFeedState(items = listOf(loadingRow(1), loadingRow(2)))

            val next = reduce(state, FeedAction.ArticleResolved(FeedId(1), article(1)))

            with(next.items[0]) {
                isArticleLoading shouldBe false
                latestArticleTitle shouldBe "Article 1"
            }
            next.items[1].isArticleLoading shouldBe true
        }

        test("RefreshSucceeded: clears every row's spinner (empty feeds resolve here)") {
            val state =
                ReducerFeedState(items = listOf(loadingRow(1), loadingRow(2)), isLoading = true)

            val next = reduce(state, FeedAction.RefreshSucceeded)

            next.isLoading shouldBe false
            next.isRefreshing shouldBe false
            next.items.forEach { it.isArticleLoading shouldBe false }
        }

        test("RefreshFailed: full-screen error only when nothing is on screen") {
            val empty = reduce(ReducerFeedState(isLoading = true), FeedAction.RefreshFailed)
            empty.isError shouldBe true

            val withRows =
                reduce(
                    ReducerFeedState(items = listOf(loadedRow(1)), isRefreshing = true),
                    FeedAction.RefreshFailed,
                )
            withRows.isError shouldBe false
            withRows.items shouldHaveSize 1
        }

        test("add-feed dialog lifecycle: open, type, succeed") {
            val opened =
                reduce(
                    ReducerFeedState(addFeedError = AddFeedError.INVALID_URL),
                    FeedAction.AddFeed.SheetOpened,
                )
            opened.isAddSheetVisible shouldBe true
            opened.addFeedError shouldBe null

            val typed = reduce(opened, FeedAction.AddFeed.UrlChanged("https://example.com"))
            typed.addUrlInput shouldBe "https://example.com"

            val submitting = reduce(typed, FeedAction.AddFeed.Started)
            submitting.isAddingFeed shouldBe true

            val done = reduce(submitting, FeedAction.AddFeed.Succeeded)
            done.isAddingFeed shouldBe false
            done.isAddSheetVisible shouldBe false
            done.addUrlInput shouldBe ""
        }

        test("AddFailed: keeps the dialog open and surfaces the error") {
            val state = ReducerFeedState(isAddSheetVisible = true, isAddingFeed = true)

            val next = reduce(state, FeedAction.AddFeed.Failed(AddFeedError.COULD_NOT_ADD))

            next.isAddingFeed shouldBe false
            next.isAddSheetVisible shouldBe true
            next.addFeedError shouldBe AddFeedError.COULD_NOT_ADD
        }

        test("delete dialog: request sets pending, confirm/cancel clear it") {
            val requested = reduce(ReducerFeedState(), FeedAction.Delete.Requested(FeedId(1)))
            requested.pendingDeletion shouldBe FeedId(1)

            reduce(requested, FeedAction.Delete.Cancelled).pendingDeletion shouldBe null
            reduce(requested, FeedAction.Delete.Confirmed).pendingDeletion shouldBe null
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

private fun loadingRow(id: Long): FeedRowUiModel =
    FeedRowUiModel(
        feedId = FeedId(id),
        title = "Feed $id",
        latestArticleTitle = null,
        latestArticlePublishedAt = null,
        isArticleLoading = true,
    )

private fun loadedRow(id: Long): FeedRowUiModel =
    FeedRowUiModel(
        feedId = FeedId(id),
        title = "Feed $id",
        latestArticleTitle = "Article $id",
        latestArticlePublishedAt = Instant.fromEpochMilliseconds(id * 1_000),
        isArticleLoading = false,
    )
