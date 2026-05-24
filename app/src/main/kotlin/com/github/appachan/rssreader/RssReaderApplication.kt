package com.github.appachan.rssreader

import android.app.Application
import com.github.appachan.rssreader.di.appModule
import com.github.appachan.rssreader.feature.feed.async.di.asyncFeedModule
import com.github.appachan.rssreader.feature.feed.di.feedModule
import com.github.appachan.rssreader.feature.feed.orbit.di.orbitFeedModule
import com.github.appachan.rssreader.feature.feed.reducer.di.reducerFeedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RssReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RssReaderApplication)
            modules(appModule, feedModule, asyncFeedModule, reducerFeedModule, orbitFeedModule)
        }
    }
}
