package com.github.appachan.rssreader.feature.feed.orbit.di

import com.github.appachan.rssreader.feature.feed.orbit.OrbitFeedListViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Pattern ④ DI. Depends only on the domain repository interfaces; the concrete implementations are
 * bound in `:app`, so this module stays decoupled from `:shared:infrastructure`. Pre-registered in
 * `RssReaderApplication`.
 */
val orbitFeedModule = module { viewModelOf(::OrbitFeedListViewModel) }
