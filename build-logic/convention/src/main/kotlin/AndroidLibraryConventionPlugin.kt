import com.android.build.api.dsl.LibraryExtension
import ext.configureAndroidCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("rssreader.quality")
                apply("rssreader.test")
            }
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
            }
        }
    }
}
