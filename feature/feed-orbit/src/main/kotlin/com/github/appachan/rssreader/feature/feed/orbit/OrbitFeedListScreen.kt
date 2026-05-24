package com.github.appachan.rssreader.feature.feed.orbit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.appachan.rssreader.shared.ui.PlaceholderScreen

/**
 * Pattern ④ (Orbit MVI). Placeholder until TASK-013 implements it. Replace the body with the real
 * feed-list screen but keep this public signature: the app's navigation entry calls it directly.
 */
@Composable
fun OrbitFeedListScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(technique = "Orbit", modifier = modifier)
}
