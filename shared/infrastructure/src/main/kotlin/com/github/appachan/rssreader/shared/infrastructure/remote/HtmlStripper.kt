package com.github.appachan.rssreader.shared.infrastructure.remote

/**
 * Reduces an RSS `<description>` (often HTML) to a plain-text snippet for list display: drops tags,
 * decodes the handful of entities feeds actually use, and collapses whitespace. Returns null when
 * nothing readable remains. Full HTML is preserved separately in `Article.content` for the article
 * detail view (#15); only the summary snippet is flattened here.
 */
internal fun stripHtml(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    val decoded =
        raw.replace(TAG_REGEX, " ")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            // &amp; last so an escaped entity like "&amp;lt;" decodes to "&lt;", not "<".
            .replace("&amp;", "&")
    return decoded.replace(WHITESPACE_REGEX, " ").trim().ifEmpty { null }
}

// Excluding '<' stops each attempt at the next '<', so unclosed '<' runs stay linear, not O(n^2).
private val TAG_REGEX = Regex("<[^<>]*>")
private val WHITESPACE_REGEX = Regex("\\s+")
