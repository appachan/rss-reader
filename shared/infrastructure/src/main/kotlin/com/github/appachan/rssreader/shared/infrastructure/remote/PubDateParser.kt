package com.github.appachan.rssreader.shared.infrastructure.remote

import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.datetime.Instant

/**
 * Parses an RSS `<pubDate>` / Atom `<published>`|`<updated>` string into an [Instant], or null when
 * absent or unrecognized. prof18/RSS-Parser hands these back as raw strings, so the two common wire
 * formats are tried in turn: RFC-822 (RSS, e.g. `Wed, 04 Jun 2025 10:30:00 +0000`) and ISO-8601
 * (Atom, e.g. `2025-06-04T10:30:00Z`). Anything else (or a blank) yields null rather than throwing,
 * since `Article.publishedAt` is nullable and an undated article is still worth showing.
 */
internal fun parsePubDate(raw: String?): Instant? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    return parseRfc822(text) ?: parseIso8601(text)
}

private fun parseRfc822(text: String): Instant? =
    runCatching {
            val zoned = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
            Instant.fromEpochMilliseconds(zoned.toInstant().toEpochMilli())
        }
        .getOrNull()

private fun parseIso8601(text: String): Instant? =
    runCatching {
            val offset = OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            Instant.fromEpochMilliseconds(offset.toInstant().toEpochMilli())
        }
        .getOrNull()
