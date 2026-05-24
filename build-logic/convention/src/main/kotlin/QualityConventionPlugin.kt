import com.diffplug.gradle.spotless.SpotlessExtension
import ext.libs
import ext.stringVersion
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("io.gitlab.arturbosch.detekt")
                apply("com.diffplug.spotless")
            }

            extensions.configure<DetektExtension> {
                config.setFrom(files("${rootProject.rootDir}/config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                autoCorrect = false
                parallel = true
            }

            val ktfmtVersion = libs.stringVersion("ktfmt")
            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("**/*.kt")
                    targetExclude("**/build/**")
                    ktfmt(ktfmtVersion).kotlinlangStyle()
                }
                kotlinGradle {
                    target("**/*.gradle.kts")
                    ktfmt(ktfmtVersion).kotlinlangStyle()
                }
            }
        }
    }
}
