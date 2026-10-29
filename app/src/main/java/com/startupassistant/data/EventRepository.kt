package com.startupassistant.data

import com.startupassistant.data.EventCategory.FUNDING
import com.startupassistant.data.EventCategory.NETWORKING
import com.startupassistant.data.EventCategory.OFFICE_SPACE
import com.startupassistant.data.EventCategory.WORKSHOP
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface EventRepository {
    fun search(query: EventQuery): List<StartupEvent>

    val eventsFlow: StateFlow<List<StartupEvent>>
        get() = MutableStateFlow(search(EventQuery())).asStateFlow()

    suspend fun refresh(
        targetCity: String = "San Francisco",
        coreSkill: String = "",
        missionAndValueProp: String = "",
    ): List<StartupEvent> = search(EventQuery(city = targetCity, text = coreSkill))
}

/**
 * Live event repository backed by [FounderEventDiscoveryService] (Engine 1: Firebase AI Logic
 * with Google Search Grounding; Engine 2: Unauthenticated Luma Public Discovery API),
 * with automatic fallback to [MockEventRepository] seed data when offline.
 */
class LiveEventRepository(
    private val discoveryService: FounderEventDiscoveryService = FounderEventDiscoveryService(),
    private val fallback: EventRepository = MockEventRepository(),
    private val today: LocalDate = LocalDate.now(),
) : EventRepository {

    private val _eventsFlow = MutableStateFlow(fallback.search(EventQuery()))
    override val eventsFlow: StateFlow<List<StartupEvent>> = _eventsFlow.asStateFlow()

    override suspend fun refresh(
        targetCity: String,
        coreSkill: String,
        missionAndValueProp: String,
    ): List<StartupEvent> {
        val discovered = runCatching {
            discoveryService.discoverFounderEvents(
                targetCity = targetCity.ifBlank { "San Francisco" },
                coreSkill = coreSkill,
                missionAndValueProp = missionAndValueProp,
            )
        }.getOrDefault(emptyList())

        if (discovered.isNotEmpty()) {
            val mapped = discovered.mapIndexed { idx, fe ->
                fe.toStartupEvent(referenceDate = today, indexOffset = idx)
            }
            val combined = (mapped + fallback.search(EventQuery()))
                .distinctBy { it.id.lowercase() }
            _eventsFlow.value = combined
        }
        return _eventsFlow.value
    }

    override fun search(query: EventQuery): List<StartupEvent> {
        val text = query.text?.trim()?.lowercase().orEmpty()
        val city = query.city?.trim()?.lowercase().orEmpty()
        return _eventsFlow.value
            .filter { query.category == null || it.category == query.category }
            .filter { !query.onlineOnly || it.online }
            .filter { !query.freeOnly || it.priceUsd == 0 }
            .filter { city.isEmpty() || it.city.lowercase().contains(city) }
            .filter { e ->
                text.isEmpty() ||
                    (listOf(e.title, e.description, e.host, e.sourcePlatform) + e.tags).any {
                        it.lowercase().contains(text)
                    }
            }
            .sortedBy { it.startsAt }
    }
}

/**
 * Seed/fallback event source used for unit tests and offline fallback.
 */
class MockEventRepository(today: LocalDate = LocalDate.now()) : EventRepository {

    private val events: List<StartupEvent> = seeds.map { s ->
        StartupEvent(
            id = s.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
            title = s.title,
            description = s.description,
            category = s.category,
            city = s.city,
            online = s.city == ONLINE,
            startsAt = today.plusDays(s.daysFromNow.toLong()).atTime(s.startHour, 0),
            durationHours = s.hours,
            host = s.host,
            priceUsd = s.price,
            tags = s.tags,
        )
    }

    override fun search(query: EventQuery): List<StartupEvent> {
        val text = query.text?.trim()?.lowercase().orEmpty()
        val city = query.city?.trim()?.lowercase().orEmpty()
        return events
            .filter { query.category == null || it.category == query.category }
            .filter { !query.onlineOnly || it.online }
            .filter { !query.freeOnly || it.priceUsd == 0 }
            .filter { city.isEmpty() || it.city.lowercase().contains(city) }
            .filter { e ->
                text.isEmpty() ||
                    (listOf(e.title, e.description, e.host) + e.tags).any { it.lowercase().contains(text) }
            }
            .sortedBy { it.startsAt }
    }

    private data class Seed(
        val title: String,
        val description: String,
        val category: EventCategory,
        val city: String,
        val daysFromNow: Int,
        val startHour: Int,
        val hours: Int,
        val host: String,
        val price: Int = 0,
        val tags: List<String>,
    )

    private companion object {
        const val ONLINE = "Online"

        val seeds = listOf(
            Seed("First-Time Founders Mixer", "Meet other people who are about to start a company. Short intros, then open networking.", NETWORKING, "San Francisco", 3, 18, 3, "Founders Collective SF", tags = listOf("cofounder", "early-stage")),
            Seed("Cofounder Matching Night", "Structured speed-matching for technical and non-technical founders who need a partner.", NETWORKING, "New York", 5, 19, 2, "NYC Startup Guild", 10, listOf("cofounder", "matching")),
            Seed("AI Builders Meetup", "Lightning demos from founders who build with AI, then networking.", NETWORKING, "Toronto", 4, 18, 3, "AI Toronto", tags = listOf("ai", "demos")),
            Seed("Women Founders Coffee", "Monthly coffee for women who are starting or thinking about starting a company.", NETWORKING, "Boston", 13, 9, 2, "Founding Women Boston", tags = listOf("community", "early-stage")),
            Seed("Pre-Seed Pitch Practice", "Pitch to three angel investors and get direct feedback on your deck.", FUNDING, "San Francisco", 7, 17, 2, "Angel Circle", tags = listOf("pitch", "angels", "pre-seed")),
            Seed("How SAFEs Work: Office Hours", "A startup lawyer explains SAFEs, caps, discounts and pro-rata rights.", FUNDING, ONLINE, 4, 12, 1, "Seedline Legal", tags = listOf("safe", "legal", "fundraising")),
            Seed("Demo Day Watch Party", "Watch an accelerator demo day with other founders and talk about what investors look for.", FUNDING, "Toronto", 12, 16, 3, "Toronto Tech Week", tags = listOf("accelerator", "demo-day")),
            Seed("Climate Tech Investor Breakfast", "Small breakfast with seed funds that invest in climate and energy startups.", FUNDING, "Boston", 9, 8, 2, "Green Seed Partners", 25, listOf("climate", "seed")),
            Seed("Grants and Non-Dilutive Funding", "An overview of government grants and tax credits for early startups.", FUNDING, ONLINE, 15, 14, 1, "Grant Navigator", tags = listOf("grants", "non-dilutive")),
            Seed("Coworking Open House", "Free day pass and tour. See hot desks, private offices and meeting rooms.", OFFICE_SPACE, "San Francisco", 2, 10, 6, "Mission Works", tags = listOf("coworking", "day-pass")),
            Seed("Startup Studio Desk Raffle", "Apply for one of ten free desks for six months. Short pitch required.", OFFICE_SPACE, "New York", 14, 15, 2, "Brooklyn Studio Labs", tags = listOf("free-desk", "studio")),
            Seed("Incubator Info Session", "Learn about the incubator program: office space, mentors and a small stipend.", OFFICE_SPACE, "Toronto", 6, 18, 1, "Queen West Incubator", tags = listOf("incubator", "mentors")),
            Seed("Remote Team Office Strategy", "Should you rent an office or stay remote? Founders share costs and lessons.", OFFICE_SPACE, ONLINE, 10, 11, 1, "Distributed Founders", tags = listOf("remote", "costs")),
            Seed("Find Your First Customers", "Hands-on session on customer interviews and validating demand before you build.", WORKSHOP, "Boston", 8, 13, 3, "Kendall Founders Lab", 15, listOf("validation", "customers")),
            Seed("Incorporation 101", "Delaware C-Corp or LLC? Learn the steps to incorporate and set up your cap table.", WORKSHOP, ONLINE, 1, 17, 1, "Seedline Legal", tags = listOf("legal", "incorporation")),
            Seed("Startup Bookkeeping Basics", "Set up your books, separate business accounts and track burn from day one.", WORKSHOP, ONLINE, 11, 12, 1, "Ledgerly", tags = listOf("finance", "bookkeeping")),
        )
    }
}
