package com.github.appachan.rssreader.feature.feed.async.di

import com.github.appachan.rssreader.feature.feed.async.AsyncFeedListViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Pattern ② DI. Depends only on the domain repository interfaces: the concrete implementations are
 * bound in `:app`, so this module stays decoupled from `:shared:infrastructure`. Pre-registered in
 * `RssReaderApplication`, so no app-level wiring change is needed.
 */
val asyncFeedModule = module { viewModelOf(::AsyncFeedListViewModel) }
