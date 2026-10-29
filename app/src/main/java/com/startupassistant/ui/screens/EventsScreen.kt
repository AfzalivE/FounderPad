package com.startupassistant.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.startupassistant.AppGraph
import com.startupassistant.data.EventCategory
import com.startupassistant.data.EventQuery
import java.time.format.DateTimeFormatter

private val whenFormat = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")

@Composable
fun EventsScreen(modifier: Modifier = Modifier) {
    var category by rememberSaveable { mutableStateOf<EventCategory?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var freeOnly by rememberSaveable { mutableStateOf(false) }
    val events = remember(category, text, city, freeOnly) {
        AppGraph.events.search(EventQuery(text = text, category = category, city = city, freeOnly = freeOnly))
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader("Events", "Meet other founders, find investors and find a place to work.")
            SampleDataNote("The Luma API needs a paid plan and shows only your own calendars.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                EventCategory.entries.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                }
                FilterChip(selected = freeOnly, onClick = { freeOnly = !freeOnly }, label = { Text("Free") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Search") }, singleLine = true)
                OutlinedTextField(city, { city = it }, Modifier.weight(1f), placeholder = { Text("City") }, singleLine = true)
            }
        }
        if (events.isEmpty()) item { Text("No events match your filters.") }
        items(events, key = { it.id }) { e ->
            InfoCard {
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${e.startsAt.format(whenFormat)} · ${if (e.online) "Online" else e.city} · ${if (e.priceUsd == 0) "Free" else "$${e.priceUsd}"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(e.description, Modifier.padding(top = 4.dp))
                Text(
                    "${e.category.label} · ${e.host} · " + e.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}
