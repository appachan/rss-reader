package com.github.appachan.rssreader.feature.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.appachan.rssreader.shared.domain.model.Article
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Flat-pattern ViewModel for one feed's article list: a single [MutableStateFlow] mutated
 * imperatively.
 *
 * The article list and the feed title are observed from the SSOT; [load] also triggers a network
 * refresh that upserts articles, after which the article observer re-emits. Pull-to-refresh keeps
 * the existing articles visible while the refresh runs.
 */
class ArticleListViewModel(
    private val articleRepository: ArticleRepository,
    private val feedRepository: FeedRepository,
    private val feedId: FeedId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArticleListUiState())
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ArticleListEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ArticleListEvent> = _events.asSharedFlow()

    private var loaded = false
    private var refreshJob: Job? = null

    /** Idempotent initial load, driven from the screen's `LaunchedEffect`. */
    fun load() {
        if (loaded) return
        loaded = true
        observeFeedTitle()
        observeArticles()
        triggerRefresh(initial = true)
    }

    /** Pull-to-refresh: keep the existing articles visible while refreshing. */
    fun refresh() {
        triggerRefresh(initial = false)
    }

    /** Re-attempt after an error. Treated as an initial load when nothing is shown yet. */
    fun retry() {
        triggerRefresh(initial = _uiState.value.items.isEmpty())
    }

    private fun observeFeedTitle(): Job = viewModelScope.launch {
        feedRepository.observeFeed(feedId).collect { feed ->
            _uiState.update { it.copy(feedTitle = feed?.title.orEmpty()) }
        }
    }

    private fun observeArticles(): Job = viewModelScope.launch {
        articleRepository.observeArticles(feedId).collect { articles ->
            _uiState.update { state -> state.copy(items = articles.map { it.toRow() }) }
        }
    }

    private fun triggerRefresh(initial: Boolean) {
        // Serialize refreshes: ignore a new request while one is still in flight.
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { state ->
                if (initial) {
                    state.copy(isLoading = true, isError = false)
                } else {
                    state.copy(isRefreshing = true, isError = false)
                }
            }
            feedRepository
                .refreshFeed(feedId)
                .fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                    },
                    onFailure = {
                        // Keep cached articles visible: full-screen error only when nothing is
                        // shown yet, otherwise a one-shot snackbar event.
                        val hadRows = _uiState.value.items.isNotEmpty()
                        _uiState.update {
                            it.copy(isLoading = false, isRefreshing = false, isError = !hadRows)
                        }
                        if (hadRows) _events.emit(ArticleListEvent.RefreshFailed)
                    },
                )
        }
    }
}

private fun Article.toRow(): ArticleRowUiModel =
    ArticleRowUiModel(articleId = id, title = title, summary = summary, publishedAt = publishedAt)
