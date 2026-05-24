package com.github.appachan.rssreader

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.github.appachan.rssreader.feature.feed.FeedListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app currently renders only the light Material3 scheme, so force dark system-bar icons
        // (SystemBarStyle.light) for contrast on the light background regardless of the system
        // dark-mode setting. Revisit when a dark color scheme is added.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme { Surface(modifier = Modifier.fillMaxSize()) { FeedListScreen() } }
        }
    }
}
