package com.github.appachan.rssreader.feature.feed.reducer.di

import org.koin.dsl.module

/**
 * Pattern ③ DI. Empty until TASK-012 adds the ViewModel (e.g.
 * `viewModelOf(::ReducerFeedListViewModel)`). Pre-registered in `RssReaderApplication`, so
 * implementing the pattern needs no app-level wiring.
 */
val reducerFeedModule = module {}
