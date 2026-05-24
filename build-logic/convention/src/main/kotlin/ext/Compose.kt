package ext

import com.android.build.api.dsl.CommonExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    commonExtension.buildFeatures.compose = true
    // Robolectric が merged resources（stringResource 等）を解決できるようにする。
    commonExtension.testOptions.unitTests.isIncludeAndroidResources = true
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

        // @Preview を ComposablePreviewScanner で自動収集してスナップショット生成する配線。
        add("testImplementation", versionCatalog.findLibrary("roborazzi-compose-preview-scanner-support").get())
        add("testImplementation", versionCatalog.findLibrary("composable-preview-scanner").get())
        add("testImplementation", versionCatalog.findLibrary("androidx-compose-ui-test-junit4").get())
        add("testImplementation", versionCatalog.findLibrary("robolectric").get())
        add("testImplementation", versionCatalog.findLibrary("junit4").get())
        // 生成される Roborazzi テストは JUnit4。useJUnitPlatform() 上で発見させるため vintage を追加 (ADR-11)。
        add("testRuntimeOnly", versionCatalog.findLibrary("junit-vintage-engine").get())
    }

    // library Compose モジュールは @Preview を自動でスナップショット対象にする。
    // application（:app）は合成ルートで自前 Preview を持たず、namespace が全 feature の接頭辞のため、
    // 有効化すると依存先 feature の Preview を classpath 走査で二重キャプチャしてしまうので除外する。
    val isLibraryModule = pluginManager.hasPlugin("com.android.library")
    extensions.configure<RoborazziExtension> {
        // golden = エージェント / 人が開く参照 PNG。全モジュール共通の相対パス。
        outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        generateComposePreviewRobolectricTests {
            enable.set(isLibraryModule)
            includePrivatePreviews.set(true)
            // packages は各モジュールの namespace から導出（namespace は module 評価後に確定するため遅延参照）。
            packages.set(provider { listOfNotNull(commonExtension.namespace) })
            // Robolectric 実行 SDK を 35 に固定。compileSdk は 36 だが、SDK 36 実行は Robolectric 4.16 で
            // JDK 21 必須。本プロジェクトは JDK 17 固定なので 35 に下げて JVM を維持する（描画は Android 15 相当）。
            robolectricConfig.set(
                mapOf(
                    "sdk" to "[35]",
                    "qualifiers" to "RobolectricDeviceQualifiers.Pixel5",
                ),
            )
        }
    }
}
