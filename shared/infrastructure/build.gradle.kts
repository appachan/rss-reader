plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.github.appachan.rssreader.shared.infrastructure"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared:domain"))
}
