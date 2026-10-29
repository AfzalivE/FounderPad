package com.startupassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.startupassistant.ui.screens.AskScreen
import com.startupassistant.ui.screens.BooksScreen
import com.startupassistant.ui.screens.EventsScreen
import com.startupassistant.ui.screens.HiringScreen
import com.startupassistant.ui.theme.StartupTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    ASK("Ask", Icons.Filled.AutoAwesome),
    EVENTS("Events", Icons.Filled.Event),
    HIRING("Hiring", Icons.Filled.Groups),
    BOOKS("Books", Icons.Filled.AccountBalanceWallet),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StartupTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.ASK) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    when (tab) {
                        Tab.ASK -> AskScreen(modifier)
                        Tab.EVENTS -> EventsScreen(modifier)
                        Tab.HIRING -> HiringScreen(modifier)
                        Tab.BOOKS -> BooksScreen(modifier)
                    }
                }
            }
        }
    }
}
