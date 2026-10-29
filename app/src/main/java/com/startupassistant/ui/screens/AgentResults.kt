package com.startupassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.startupassistant.AppGraph
import com.startupassistant.data.EventCategory
import com.startupassistant.data.EventQuery
import com.startupassistant.data.StartupEvent

/*
 * Agent-to-UI results: when the agent calls a read tool, the client runs the
 * same query and shows native cards, so the model's reply can stay short.
 */

private const val MAX_EVENT_CARDS = 5

/** A tool result the Ask tab shows as native components. */
sealed interface AgentResult {
    data class Events(val title: String, val events: List<StartupEvent>, val more: Int) : AgentResult
    data object Runway : AgentResult
}

/** Builds the result for a finished tool, from the args of each of its calls. */
internal fun agentResult(toolName: String, calls: List<String>): AgentResult? = when (toolName) {
    "find_events" -> {
        val events = calls.flatMap { searchEvents(parseToolArgs(it)) }.distinctBy { it.id }
        AgentResult.Events(
            title = eventsTitle(events.size, parseToolArgs(calls.lastOrNull().orEmpty())),
            events = events.take(MAX_EVENT_CARDS),
            more = (events.size - MAX_EVENT_CARDS).coerceAtLeast(0),
        )
    }
    "get_runway" -> AgentResult.Runway
    else -> null
}

/**
 * Parses tool args from Map.toString ("{freeOnly=true, category=funding}") or
 * loose JSON ("{\"freeOnly\": true}").
 */
internal fun parseToolArgs(args: String): Map<String, String> =
    args.trim().removePrefix("{").removeSuffix("}")
        .split(',')
        .mapNotNull { part ->
            val at = part.indexOfFirst { it == '=' || it == ':' }
            if (at < 0) return@mapNotNull null
            val key = part.substring(0, at).trim().trim('"', '\'')
            val value = part.substring(at + 1).trim().trim('"', '\'')
            key.takeIf { it.isNotEmpty() }?.let { it to value }
        }
        .toMap()

/** "4 free funding events online", from the count and the last call's args. */
private fun eventsTitle(count: Int, args: Map<String, String>): String {
    if (count == 0) return "No events match. Try the Events tab."
    val city = args["city"]?.takeIf { it.isNotBlank() && it != "null" }
    val topic = args["topic"]?.takeIf { it.isNotBlank() && it != "null" }
    val kind = when (parseCategory(args["category"])) {
        EventCategory.NETWORKING -> "networking"
        EventCategory.FUNDING -> "funding"
        EventCategory.OFFICE_SPACE -> "office space"
        EventCategory.WORKSHOP -> "workshop"
        null -> null
    }
    return buildString {
        append(count)
        if (args["freeOnly"].toBoolean()) append(" free")
        kind?.let { append(" ").append(it) }
        append(if (count == 1) " event" else " events")
        when {
            city.equals("online", ignoreCase = true) -> append(" online")
            city != null -> append(" in ").append(city)
        }
        topic?.takeIf { !it.equals(kind, ignoreCase = true) }?.let { append(" about ").append(it) }
    }
}

/** Runs the same search as StartupTools.find_events. */
private fun searchEvents(args: Map<String, String>): List<StartupEvent> {
    val city = args["city"]?.takeIf { it.isNotBlank() && it != "null" }
    val online = city.equals("online", ignoreCase = true)
    return AppGraph.events.search(
        EventQuery(
            text = args["topic"]?.takeIf { it.isNotBlank() && it != "null" },
            category = parseCategory(args["category"]),
            city = if (online) null else city,
            onlineOnly = online,
            freeOnly = args["freeOnly"].toBoolean(),
        ),
    )
}

private fun parseCategory(value: String?): EventCategory? {
    val v = value?.trim()?.lowercase() ?: return null
    return when (v) {
        "people", "networking", "cofounder" -> EventCategory.NETWORKING
        "funding", "investors", "fundraising" -> EventCategory.FUNDING
        "office", "office-space", "office space", "coworking" -> EventCategory.OFFICE_SPACE
        "workshop", "workshops", "learning" -> EventCategory.WORKSHOP
        else -> EventCategory.entries.firstOrNull { it.name.equals(v, ignoreCase = true) || it.label.equals(v, ignoreCase = true) }
    }
}

@Composable
internal fun AgentResultView(result: AgentResult, modifier: Modifier = Modifier) {
    when (result) {
        is AgentResult.Events -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp).enterStagger(0),
            )
            result.events.forEachIndexed { i, e ->
                EventCard(e, compact = true, modifier = Modifier.enterStagger(i + 1))
            }
            if (result.more > 0) {
                Text(
                    "${result.more} more in the Events tab",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        AgentResult.Runway -> {
            val s = AppGraph.books.summary()
            CashHero(s.cashBalanceCents, s.averageMonthlyBurnCents, s.runwayMonths, modifier.enterStagger(0))
        }
    }
}
