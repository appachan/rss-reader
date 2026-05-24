package com.github.appachan.rssreader.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Full-screen placeholder used by the pattern modules (②③④) that are not implemented yet. Each
 * `<Technique>FeedListScreen` delegates here until its real implementation lands (TASK-011〜013), so
 * the bottom-navigation shell can route to every pattern from day one.
 */
@Composable
fun PlaceholderScreen(technique: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.placeholder_coming_soon, technique),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    MaterialTheme { Surface { PlaceholderScreen(technique = "Async") } }
}
