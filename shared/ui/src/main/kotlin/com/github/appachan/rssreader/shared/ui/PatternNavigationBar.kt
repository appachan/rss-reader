package com.github.appachan.rssreader.shared.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Stateless bottom navigation bar for switching between the UI state-management patterns. Decoupled
 * from the navigation backstack: the caller maps its destinations to [tabs] labels and reflects the
 * current destination via [selectedIndex]. All patterns render the same feed-list screen, so every
 * item shares one neutral icon and is distinguished by its technique-name label.
 */
@Composable
fun PatternNavigationBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        tabs.forEachIndexed { index, label ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = null)
                },
                label = { Text(label) },
            )
        }
    }
}

@Preview
@Composable
private fun PatternNavigationBarPreview() {
    MaterialTheme {
        PatternNavigationBar(
            tabs = listOf("Flat", "Async", "Reducer", "Orbit"),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}
