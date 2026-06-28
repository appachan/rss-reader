package com.github.appachan.rssreader.feature.article.di

import com.github.appachan.rssreader.feature.article.ArticleListViewModel
import com.github.appachan.rssreader.shared.domain.model.FeedId
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Feature DI. Depends only on the domain repository interfaces: the concrete implementations are
 * bound in `:app` via `:shared:infrastructure`. feedId is supplied at navigation time via
 * `parametersOf(FeedId(...))`.
 */
val articleModule = module {
    viewModel { (feedId: FeedId) -> ArticleListViewModel(get(), get(), feedId) }
}
