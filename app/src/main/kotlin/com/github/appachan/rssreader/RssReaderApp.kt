package com.github.appachan.rssreader

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.github.appachan.rssreader.feature.article.ArticleListScreen
import com.github.appachan.rssreader.feature.feed.FeedListScreen
import com.github.appachan.rssreader.feature.feed.async.AsyncFeedListScreen
import com.github.appachan.rssreader.feature.feed.orbit.OrbitFeedListScreen
import com.github.appachan.rssreader.feature.feed.reducer.ReducerFeedListScreen
import com.github.appachan.rssreader.navigation.ArticleListKey
import com.github.appachan.rssreader.navigation.AsyncPattern
import com.github.appachan.rssreader.navigation.FlatPattern
import com.github.appachan.rssreader.navigation.OrbitPattern
import com.github.appachan.rssreader.navigation.PatternTab
import com.github.appachan.rssreader.navigation.ReducerPattern
import com.github.appachan.rssreader.shared.domain.model.FeedId
import com.github.appachan.rssreader.shared.ui.PatternNavigationBar

/**
 * App shell: a persistent bottom navigation bar over a Nav3 [NavDisplay] that routes to each
 * pattern's feed-list screen. Each pattern is a single top-level destination (no nested
 * navigation), so a tab tap replaces the whole backstack with that pattern's key.
 */
@Composable
fun RssReaderApp() {
    val backStack = rememberNavBackStack(FlatPattern)
    val tabs = PatternTab.entries
    val selectedIndex = tabs.indexOfFirst { it.key == backStack.lastOrNull() }.coerceAtLeast(0)

    Scaffold(
        // The bottom bar owns the navigation-bar inset; each pattern screen's own Scaffold owns the
        // top inset. Keeping the shell's content insets at zero (and consuming innerPadding below)
        // avoids the system bars being padded twice by the nested Scaffolds.
        contentWindowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
        bottomBar = {
            PatternNavigationBar(
                tabs = tabs.map { it.label },
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    val key = tabs[index].key
                    if (backStack.lastOrNull() != key) {
                        backStack.clear()
                        backStack.add(key)
                    }
                },
            )
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            entryProvider =
                entryProvider {
                    entry<FlatPattern> {
                        FeedListScreen(
                            onFeedClick = { feedId -> backStack.add(ArticleListKey(feedId.value)) }
                        )
                    }
                    entry<AsyncPattern> { AsyncFeedListScreen() }
                    entry<ReducerPattern> { ReducerFeedListScreen() }
                    entry<OrbitPattern> {
                        OrbitFeedListScreen(
                            onFeedClick = { feedId -> backStack.add(ArticleListKey(feedId.value)) }
                        )
                    }
                    entry<ArticleListKey> { key ->
                        ArticleListScreen(
                            feedId = FeedId(key.feedId),
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
        )
    }
}
