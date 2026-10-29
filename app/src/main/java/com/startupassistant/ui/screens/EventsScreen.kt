package com.startupassistant.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.startupassistant.AppGraph
import com.startupassistant.data.EventCategory
import com.startupassistant.data.EventQuery
import com.startupassistant.data.StartupEvent
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("EEE · h:mm a")
private val monthFormat = DateTimeFormatter.ofPattern("MMM")

private val EventCategory.icon: ImageVector
    get() = when (this) {
        EventCategory.NETWORKING -> Icons.Filled.Groups
        EventCategory.FUNDING -> Icons.Filled.Paid
        EventCategory.OFFICE_SPACE -> Icons.Filled.Business
        EventCategory.WORKSHOP -> Icons.Filled.School
    }

@Composable
private fun EventCategory.colors(): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when (this@colors) {
        EventCategory.NETWORKING -> primaryContainer to onPrimaryContainer
        EventCategory.FUNDING -> tertiaryContainer to onTertiaryContainer
        EventCategory.OFFICE_SPACE -> secondaryContainer to onSecondaryContainer
        EventCategory.WORKSHOP -> surfaceContainerHighest to onSurface
    }
}

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column {
                ScreenHeader("Events", "Meet other founders, find investors and find a place to work.", Icons.Filled.Event, TabShapes.events)
                SampleDataNote("The Luma API needs a paid plan and shows only your own calendars.")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).enterStagger(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                    EventCategory.entries.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = if (category == c) null else c },
                            label = { Text(c.label) },
                            leadingIcon = { Icon(c.icon, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
                        )
                    }
                    FilterChip(
                        selected = freeOnly,
                        onClick = { freeOnly = !freeOnly },
                        label = { Text("Free") },
                        leadingIcon = { Icon(Icons.Filled.Sell, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
                Row(
                    Modifier.padding(top = 8.dp).enterStagger(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        text, { text = it }, Modifier.weight(1f),
                        placeholder = { Text("Search") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        shape = RoundedCornerShape(50),
                    )
                    OutlinedTextField(
                        city, { city = it }, Modifier.weight(1f),
                        placeholder = { Text("City") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }
        if (events.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp).animateItem().enterStagger(0),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ShapeBadge(
                        Icons.Filled.EventBusy, TabShapes.events, size = 80.dp, spin = true,
                        colors = listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHigh),
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("No events match your filters.", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        itemsIndexed(events, key = { _, e -> e.id }) { i, e ->
            EventCard(e, Modifier.animateItem().enterStagger(i + 4))
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(e: StartupEvent, modifier: Modifier = Modifier) {
    val (container, content) = e.category.colors()
    InfoCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Calendar tile
            Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(e.startsAt.format(monthFormat).uppercase(), style = MaterialTheme.typography.labelMedium)
                    Text(e.startsAt.dayOfMonth.toString(), style = MaterialTheme.typography.headlineMedium)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    PriceBadge(e.priceUsd)
                }
                Text(
                    "${e.startsAt.format(timeFormat)} · ${if (e.online) "Online" else e.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(
            e.description,
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tag(e.category.label, e.category.icon, container, content)
            Tag(e.host, null, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
            e.tags.forEach { Tag("#$it", null, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PriceBadge(priceUsd: Int) {
    val free = priceUsd == 0
    Surface(
        color = if (free) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (free) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            if (free) "Free" else "$$priceUsd",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun Tag(text: String, icon: ImageVector?, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
