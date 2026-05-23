import com.ncorti.ktfmt.gradle.KtfmtExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("io.gitlab.arturbosch.detekt")
                apply("com.ncorti.ktfmt.gradle")
            }

            extensions.configure<DetektExtension> {
                config.setFrom(files("${rootProject.rootDir}/config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                autoCorrect = false
                parallel = true
            }

            extensions.configure<KtfmtExtension> {
                kotlinLangStyle()
            }
        }
    }
}
