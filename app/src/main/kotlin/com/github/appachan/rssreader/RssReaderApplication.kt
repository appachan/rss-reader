package com.github.appachan.rssreader

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RssReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RssReaderApplication)
            modules(emptyList())
        }
    }
}
