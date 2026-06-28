package com.github.appachan.rssreader.shared.infrastructure.remote

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class PubDateParserTest :
    FunSpec({
        // 2025-06-04T10:30:00Z == 1749033000000 ms
        val expected = Instant.fromEpochMilliseconds(1_749_033_000_000)

        test("parses RFC-822 (RSS) dates") {
            parsePubDate("Wed, 04 Jun 2025 10:30:00 GMT") shouldBe expected
        }

        test("parses RFC-822 dates with a numeric offset") {
            parsePubDate("Wed, 04 Jun 2025 12:30:00 +0200") shouldBe expected
        }

        test("parses ISO-8601 (Atom) dates") {
            parsePubDate("2025-06-04T10:30:00Z") shouldBe expected
        }

        test("returns null for blank or unparseable input") {
            parsePubDate(null).shouldBeNull()
            parsePubDate("").shouldBeNull()
            parsePubDate("   ").shouldBeNull()
            parsePubDate("not a date").shouldBeNull()
        }
    })
