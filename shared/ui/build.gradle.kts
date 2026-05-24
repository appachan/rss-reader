plugins {
    id("rssreader.android.library")
    id("rssreader.android.compose")
}

android { namespace = "com.github.appachan.rssreader.shared.ui" }

dependencies { implementation(libs.androidx.compose.material.icons.core) }
