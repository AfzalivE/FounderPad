package com.startupassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import com.startupassistant.ui.screens.AskScreen
import com.startupassistant.ui.screens.BooksScreen
import com.startupassistant.ui.screens.EventsScreen
import com.startupassistant.ui.screens.HiringScreen
import com.startupassistant.ui.theme.StartupTheme

private enum class Tab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    ASK("Ask", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    EVENTS("Events", Icons.Filled.Event, Icons.Outlined.Event),
    HIRING("Hiring", Icons.Filled.Groups, Icons.Outlined.Groups),
    BOOKS("Books", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StartupTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.ASK) }
                // Keeps each tab's text fields and scroll position when you switch tabs.
                val tabState = rememberSaveableStateHolder()
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            Tab.entries.forEach { t ->
                                val selected = tab == t
                                // The icon pops a little when its tab is selected.
                                val scale by animateFloatAsState(
                                    if (selected) 1.15f else 1f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    label = "iconScale",
                                )
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { tab = t },
                                    icon = {
                                        Icon(
                                            if (selected) t.selectedIcon else t.icon,
                                            contentDescription = null,
                                            modifier = Modifier.scale(scale),
                                        )
                                    },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    AnimatedContent(
                        targetState = tab,
                        // Consume the bottom bar's space so imePadding in Ask adds only the rest.
                        modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                        transitionSpec = {
                            // Slide in the direction of the tab, like pages.
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            val slide = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                            (slideInHorizontally(slide) { it / 4 * dir } + fadeIn() + scaleIn(initialScale = 0.98f)) togetherWith
                                (slideOutHorizontally(slide) { -it / 4 * dir } + fadeOut())
                        },
                        label = "tab",
                    ) { t ->
                        tabState.SaveableStateProvider(t.name) {
                            when (t) {
                                Tab.ASK -> AskScreen()
                                Tab.EVENTS -> EventsScreen()
                                Tab.HIRING -> HiringScreen()
                                Tab.BOOKS -> BooksScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
