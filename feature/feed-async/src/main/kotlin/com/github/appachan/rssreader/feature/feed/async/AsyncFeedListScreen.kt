package com.github.appachan.rssreader.feature.feed.async

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.appachan.rssreader.shared.ui.PlaceholderScreen

/**
 * Pattern ② (Async 型 + combine). Placeholder until TASK-011 implements it. Replace the body with
 * the real feed-list screen but keep this public signature: the app's navigation entry calls it
 * directly.
 */
@Composable
fun AsyncFeedListScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(technique = "Async", modifier = modifier)
}
