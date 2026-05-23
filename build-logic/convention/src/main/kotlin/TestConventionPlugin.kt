import ext.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class TestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val parallelism = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                // Kotest in-JVM thread pool. Spec-level parallelism still requires
                // each spec to opt in via ProjectConfig / spec config — this sets the
                // ceiling, not the policy.
                systemProperty("kotest.framework.parallelism", parallelism.toString())
                // Pin the ProjectConfig FQN so Kotest skips classpath scanning and
                // there's no ambiguity if a stray AbstractProjectConfig appears.
                systemProperty(
                    "kotest.framework.config.fqn",
                    "com.github.appachan.rssreader.testing.RssReaderTestConfig",
                )
            }

            val versionCatalog = libs
            dependencies {
                add("testImplementation", versionCatalog.findLibrary("kotest-runner-junit5").get())
                add("testImplementation", versionCatalog.findLibrary("kotest-assertions-core").get())
                add("testImplementation", versionCatalog.findLibrary("kotlinx-coroutines-test").get())
                add("testImplementation", versionCatalog.findLibrary("turbine").get())
                add("testImplementation", versionCatalog.findLibrary("mockk").get())

                // The shared testing module itself cannot depend on itself.
                if (path != ":shared:testing") {
                    add("testImplementation", project(":shared:testing"))
                }
            }
        }
    }
}
