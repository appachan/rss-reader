plugins {
    id("rssreader.android.application")
    id("rssreader.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.appachan.rssreader"
    defaultConfig {
        applicationId = "com.github.appachan.rssreader"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(projects.feature.feed)
    implementation(projects.shared.domain)
    implementation(projects.shared.infrastructure)
    implementation(projects.shared.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.compose.navigation3)
}
