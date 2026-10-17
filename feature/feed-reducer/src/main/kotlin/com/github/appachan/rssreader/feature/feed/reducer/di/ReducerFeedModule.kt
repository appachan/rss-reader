package com.github.appachan.rssreader.feature.feed.reducer.di

import com.github.appachan.rssreader.feature.feed.reducer.ReducerFeedListViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Pattern ③ DI. Depends only on the domain repository interfaces: the concrete implementations are
 * bound in `:app`, so this module stays decoupled from `:shared:infrastructure`. Pre-registered in
 * `RssReaderApplication`, so the pattern needs no app-level wiring.
 */
val reducerFeedModule = module { viewModelOf(::ReducerFeedListViewModel) }
