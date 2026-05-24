package com.github.appachan.rssreader.feature.feed.reducer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.appachan.rssreader.shared.ui.PlaceholderScreen

/**
 * Pattern ③ (State reducer / 素の ViewModel). Placeholder until TASK-012 implements it. Replace the
 * body with the real feed-list screen but keep this public signature: the app's navigation entry
 * calls it directly.
 */
@Composable
fun ReducerFeedListScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(technique = "Reducer", modifier = modifier)
}
