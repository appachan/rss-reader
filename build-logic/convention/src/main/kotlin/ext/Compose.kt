package ext

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    commonExtension.buildFeatures.compose = true
    pluginManager.apply("io.github.takahirom.roborazzi")

    val versionCatalog = libs
    dependencies {
        val composeBom = versionCatalog.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(composeBom))
        add("androidTestImplementation", platform(composeBom))

        add("implementation", versionCatalog.findLibrary("androidx-compose-ui").get())
        add("implementation", versionCatalog.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", versionCatalog.findLibrary("androidx-compose-material3").get())

        add("debugImplementation", versionCatalog.findLibrary("androidx-compose-ui-tooling").get())

        // Compose 系モジュールは Roborazzi で VRT する前提 (ADR-11)。
        add("testImplementation", versionCatalog.findLibrary("roborazzi").get())
        add("testImplementation", versionCatalog.findLibrary("roborazzi-compose").get())
        add("testImplementation", versionCatalog.findLibrary("roborazzi-junit-rule").get())
    }
}
