package com.github.appachan.rssreader.feature.feed.di

import com.github.appachan.rssreader.feature.feed.FeedListViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Feature DI. Depends only on the domain repository interfaces: the concrete implementations are
 * bound in `:app`, so this module stays decoupled from `:shared:infrastructure`.
 */
val feedModule = module { viewModelOf(::FeedListViewModel) }
