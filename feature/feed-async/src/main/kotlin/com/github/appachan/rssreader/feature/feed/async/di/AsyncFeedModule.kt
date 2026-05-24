package com.github.appachan.rssreader.feature.feed.async.di

import org.koin.dsl.module

/**
 * Pattern ② DI. Empty until TASK-011 adds the ViewModel (e.g.
 * `viewModelOf(::AsyncFeedListViewModel)`). Pre-registered in `RssReaderApplication`, so
 * implementing the pattern needs no app-level wiring.
 */
val asyncFeedModule = module {}
