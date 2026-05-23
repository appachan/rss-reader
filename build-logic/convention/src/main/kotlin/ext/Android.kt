package ext

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal fun Project.configureAndroidCommon(commonExtension: CommonExtension) {
    val versionCatalog = libs
    commonExtension.compileSdk = versionCatalog.intVersion("compileSdk")
    commonExtension.defaultConfig.minSdk = versionCatalog.intVersion("minSdk")

    commonExtension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    commonExtension.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    commonExtension.buildFeatures.aidl = false
    commonExtension.buildFeatures.buildConfig = false
    commonExtension.buildFeatures.shaders = false

    commonExtension.lint.warningsAsErrors = true
    commonExtension.lint.abortOnError = true
    commonExtension.lint.checkDependencies = true
    // Version-bump advisories fire perpetually as upstream releases new artifacts
    // and don't catch real bugs. Suppress them while keeping every other Lint
    // check enforced (ADR-12).
    commonExtension.lint.disable.addAll(
        listOf(
            "NewerVersionAvailable",
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
            // mipmap-anydpi-v26 is the Android conventional folder for adaptive
            // icons. Keep the qualifier to make the intent obvious even though
            // minSdk >= 26.
            "ObsoleteSdkInt",
        ),
    )

    extensions.getByType<KotlinAndroidProjectExtension>().apply {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
