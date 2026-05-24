package com.github.appachan.rssreader.shared.infrastructure.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.github.appachan.rssreader.shared.domain.repository.ArticleRepository
import com.github.appachan.rssreader.shared.domain.repository.FeedRepository
import com.github.appachan.rssreader.shared.infrastructure.db.FeedDatabase
import com.github.appachan.rssreader.shared.infrastructure.remote.FeedFetcher
import com.github.appachan.rssreader.shared.infrastructure.remote.RssParserFeedFetcher
import com.github.appachan.rssreader.shared.infrastructure.repository.ArticleRepositoryImpl
import com.github.appachan.rssreader.shared.infrastructure.repository.FeedRepositoryImpl
import com.prof18.rssparser.RssParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The only place that knows the data-layer implementations: `:feature:*` stays compile-time
 * decoupled and resolves the domain interfaces at runtime via Koin. Android-only types
 * (`AndroidSqliteDriver` needs a `Context`) are confined here behind `androidContext()`.
 */
val infrastructureModule = module {
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = FeedDatabase.Schema,
            context = androidContext(),
            name = "FeedDatabase",
        )
    }
    single { FeedDatabase(get()) }

    single {
        HttpClient(OkHttp) {
            // Surface non-2xx as a failure so addFeed validation rejects unreachable feeds.
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
            }
            install(Logging)
        }
    }
    single { RssParser() }
    single<FeedFetcher> { RssParserFeedFetcher(get(), get()) }

    single<FeedRepository> { FeedRepositoryImpl(get(), get(), Dispatchers.IO) }
    single<ArticleRepository> { ArticleRepositoryImpl() }
}

private const val REQUEST_TIMEOUT_MS = 15_000L
private const val CONNECT_TIMEOUT_MS = 10_000L
