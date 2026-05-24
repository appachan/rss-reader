package com.github.appachan.rssreader.feature.feed.orbit.di

import org.koin.dsl.module

/**
 * Pattern ④ DI. Empty until TASK-013 adds the ViewModel (e.g.
 * `viewModelOf(::OrbitFeedListViewModel)`). Pre-registered in `RssReaderApplication`, so
 * implementing the pattern needs no app-level wiring.
 */
val orbitFeedModule = module {}
