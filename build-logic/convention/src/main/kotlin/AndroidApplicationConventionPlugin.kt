import com.android.build.api.dsl.ApplicationExtension
import ext.configureAndroidCommon
import ext.intVersion
import ext.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("rssreader.quality")
                apply("rssreader.test")
            }
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = libs.intVersion("targetSdk")
            }
        }
    }
}
