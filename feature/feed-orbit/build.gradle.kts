plugins {
    id("rssreader.android.library")
    id("rssreader.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.github.appachan.rssreader.feature.feed.orbit" }

dependencies {
    implementation(projects.shared.domain)
    implementation(projects.shared.ui)

    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.compose.navigation3)

    // Pattern ④: Orbit MVI (state reducer DSL + side effects, KMP-ready).
    implementation(libs.orbit.core)
    implementation(libs.orbit.viewmodel)
    implementation(libs.orbit.compose)

    testImplementation(libs.orbit.test)
}
