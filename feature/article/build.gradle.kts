plugins {
    id("rssreader.android.library")
    id("rssreader.android.compose")
}

android { namespace = "com.github.appachan.rssreader.feature.article" }

dependencies {
    implementation(projects.shared.domain)

    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.compose.navigation3)
}
