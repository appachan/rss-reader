package com.github.appachan.rssreader.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.appachan.rssreader.shared.domain.model.Feed
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/**
 * Pattern ① ViewModel for the feed list. State management is intentionally the naive flat approach:
 * a single [MutableStateFlow] of [FeedListUiState] mutated imperatively, no Async wrapper and no
 * `combine`-as-architecture (those are TASK-011〜014).
 *
 * Dependency-ordered parallel fetch: the feed list is observed first; for each feed an independent
 * coroutine resolves its latest article and updates only that row, so rows fill in as their data
 * arrives. Pull-to-refresh keeps the existing rows visible while [refreshAllFeeds] re-populates the
 * single source of truth.
 */
class FeedListViewModel(
    private val feedRepository: FeedRepository,
    private val articleRepository: ArticleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedListUiState())
    val uiState: StateFlow<FeedListUiState> = _uiState.asStateFlow()

    private var feedsJob: Job? = null
    private var articleJobs: List<Job> = emptyList()

    init {
        load()
    }

    /** Initial load: start observing the SSOT and trigger the first network refresh. */
    fun load() {
        if (feedsJob == null) {
            feedsJob = observeFeeds()
        }
        triggerRefresh(initial = true)
    }

    /**
     * Pull-to-refresh: keep showing existing rows while
     * [isRefreshing][FeedListUiState.isRefreshing].
     */
    fun refresh() {
        triggerRefresh(initial = false)
    }

    /** Re-attempt after an error. Treated as an initial load when nothing is shown yet. */
    fun retry() {
        triggerRefresh(initial = _uiState.value.items.isEmpty())
    }

    private fun observeFeeds(): Job = viewModelScope.launch {
        feedRepository.observeFeeds().collect { feeds ->
            _uiState.update { state -> state.copy(items = reconcileRows(state.items, feeds)) }
            restartArticleJobs(feeds)
        }
    }

    private fun reconcileRows(
        current: List<FeedRowUiModel>,
        feeds: List<Feed>,
    ): List<FeedRowUiModel> {
        val existing = current.associateBy { it.feedId }
        return feeds.map { feed ->
            // Preserve an already-resolved article so a refresh doesn't flip the row back to
            // loading.
            existing[feed.id]?.copy(title = feed.title)
                ?: FeedRowUiModel(
                    feedId = feed.id,
                    title = feed.title,
                    latestArticleTitle = null,
                    latestArticlePublishedAt = null,
                    isArticleLoading = true,
                )
        }
    }

    private fun restartArticleJobs(feeds: List<Feed>) {
        articleJobs.forEach { it.cancel() }
        articleJobs = feeds.map { feed -> launchLatestArticle(feed) }
    }

    private fun launchLatestArticle(feed: Feed): Job = viewModelScope.launch {
        articleRepository.observeArticles(feed.id).collect { articles ->
            // Empty means "not fetched yet" (the SSOT cache starts empty); keep the row loading
            // until articles actually arrive. A genuinely empty feed is resolved when the
            // surrounding refresh completes (see triggerRefresh).
            if (articles.isEmpty()) return@collect
            val latest = articles.maxByOrNull { it.publishedAt ?: Instant.DISTANT_PAST }
            _uiState.update { state ->
                state.copy(
                    items =
                        state.items.map { row ->
                            if (row.feedId == feed.id) {
                                row.copy(
                                    latestArticleTitle = latest?.title,
                                    latestArticlePublishedAt = latest?.publishedAt,
                                    isArticleLoading = false,
                                )
                            } else {
                                row
                            }
                        }
                )
            }
        }
    }

    private fun triggerRefresh(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update { state ->
                if (initial) {
                    state.copy(isLoading = true, error = null)
                } else {
                    state.copy(isRefreshing = true, error = null)
                }
            }
            val result = feedRepository.refreshAllFeeds()
            _uiState.update { state ->
                result.fold(
                    onSuccess = {
                        // Refresh done: any row still loading simply has no articles.
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            items = state.items.map { it.copy(isArticleLoading = false) },
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "フィードの更新に失敗しました",
                        )
                    },
                )
            }
        }
    }
}
