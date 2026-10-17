package com.github.appachan.rssreader.shared.infrastructure.remote

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class HtmlStripperTest :
    FunSpec({
        test("drops tags and collapses whitespace") {
            stripHtml("<p>Article URL: <a href=\"https://example.com\">link</a></p>") shouldBe
                "Article URL: link"
        }

        test("decodes common entities, &amp; last") {
            stripHtml("Tom &amp; Jerry &lt;tag&gt; &quot;q&quot; &amp;lt;") shouldBe
                "Tom & Jerry <tag> \"q\" &lt;"
        }

        test("returns null for null, empty, or markup-only input") {
            stripHtml(null).shouldBeNull()
            stripHtml("").shouldBeNull()
            stripHtml("<br/>   <br/>").shouldBeNull()
        }

        test("leaves plain text untouched apart from trimming") {
            stripHtml("  plain summary  ") shouldBe "plain summary"
        }

        test("handles a long run of unclosed '<' in linear time").config(timeout = 2.seconds) {
            val raw = "<".repeat(200_000)
            stripHtml(raw) shouldBe raw
        }
    })
