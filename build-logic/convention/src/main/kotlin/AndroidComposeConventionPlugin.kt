import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import ext.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val commonExtension: CommonExtension = when {
                pluginManager.hasPlugin("com.android.application") ->
                    extensions.getByType(ApplicationExtension::class.java)
                pluginManager.hasPlugin("com.android.library") ->
                    extensions.getByType(LibraryExtension::class.java)
                else -> error(
                    "rssreader.android.compose requires either com.android.application or " +
                        "com.android.library to be applied first.",
                )
            }
            configureAndroidCompose(commonExtension)
        }
    }
}
