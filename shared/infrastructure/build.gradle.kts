plugins {
    id("rssreader.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

android { namespace = "com.github.appachan.rssreader.shared.infrastructure" }

sqldelight {
    databases {
        create("FeedDatabase") {
            packageName.set("com.github.appachan.rssreader.shared.infrastructure.db")
        }
    }
}

dependencies {
    api(projects.shared.domain)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines.extensions)

    implementation(libs.androidx.work.runtime)
}
