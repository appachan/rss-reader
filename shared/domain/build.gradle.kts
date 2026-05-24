plugins {
    id("rssreader.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    // Exposed in repository/model public API (Flow<…>, Instant), so consumers compile against them.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
}
