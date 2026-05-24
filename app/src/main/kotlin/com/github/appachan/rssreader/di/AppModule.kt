package com.github.appachan.rssreader.di

import com.github.appachan.rssreader.data.fake.FakeArticleRepository
import com.github.appachan.rssreader.data.fake.FakeFeedRepository
import com.github.appachan.rssreader.data.fake.FakeRssStore
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import org.koin.dsl.module

/**
 * App-level wiring. Until the real Ktor/SQLDelight infrastructure module exists (a separate task),
 * an in-memory [FakeRssStore] backs both domain repositories so the feed-list screen runs
 * end-to-end.
 */
val appModule = module {
    single { FakeRssStore() }
    single<FeedRepository> { FakeFeedRepository(get()) }
    single<ArticleRepository> { FakeArticleRepository(get()) }
}
