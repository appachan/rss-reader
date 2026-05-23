package com.github.appachan.rssreader.testing

import io.kotest.core.config.AbstractProjectConfig

/**
 * Project-wide Kotest config picked up by every module via TestConventionPlugin. Enables
 * coroutineTestScope so suspend tests can use virtual time without an explicit runTest { } wrapper.
 */
class RssReaderTestConfig : AbstractProjectConfig() {
    override var coroutineTestScope: Boolean? = true
}
