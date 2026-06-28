plugins { id("rssreader.kotlin.jvm") }

dependencies {
    api(libs.kotest.runner.junit5)
    // Reusable fakes implement domain interfaces, so consumers compile against them.
    api(projects.shared.domain)
}
