package com.startupassistant.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRepositoryTest {
    private val today = LocalDate.of(2026, 1, 10)
    private val repo = MockEventRepository(today)
    private val discoveryService = FounderEventDiscoveryService()

    @Test
    fun returnsUpcomingEventsSoonestFirst() {
        val events = repo.search(EventQuery())
        assertTrue(events.size > 10)
        assertTrue(events.all { it.startsAt.toLocalDate().isAfter(today) })
        assertEquals(events.sortedBy { it.startsAt }, events)
    }

    @Test
    fun filtersByCategoryTextCityOnlineAndPrice() {
        assertTrue(repo.search(EventQuery(category = EventCategory.OFFICE_SPACE)).all { it.category == EventCategory.OFFICE_SPACE })
        assertTrue(repo.search(EventQuery(text = "safe")).any { it.id == "how-safes-work-office-hours" })
        assertTrue(repo.search(EventQuery(city = "toronto")).all { it.city == "Toronto" })
        assertTrue(repo.search(EventQuery(onlineOnly = true)).all { it.online })
        assertTrue(repo.search(EventQuery(freeOnly = true)).all { it.priceUsd == 0 })
    }

    @Test
    fun mapsFounderEventToStartupEvent() {
        val founderEvent = FounderEvent(
            id = "evt-test-1",
            title = "Mountain View AI Cofounder Mixer",
            category = "Cofounder_Meetup",
            dateAndTime = "2026-10-30T01:00:00.000Z",
            locationOrVenue = "Mountain View, CA",
            hostOrOrganizer = "Bay Area Builders",
            registrationUrl = "https://lu.ma/mv-ai-mixer",
            matchRationale = "Direct match for AI founders in Mountain View.",
            sourcePlatform = "Luma",
        )
        val startupEvent = founderEvent.toStartupEvent(today)
        assertEquals("evt-test-1", startupEvent.id)
        assertEquals(EventCategory.NETWORKING, startupEvent.category)
        assertEquals("Mountain View, CA", startupEvent.city)
        assertEquals("https://lu.ma/mv-ai-mixer", startupEvent.registrationUrl)
        assertEquals("Luma", startupEvent.sourcePlatform)
    }

    @Test
    fun mapsCityToRealLumaDiscoverId() {
        assertEquals("discplace-BDj7GNbGlsF7Cka", discoveryService.mapCityToLumaDiscoverId("Mountain View, CA"))
        assertEquals("discplace-BDj7GNbGlsF7Cka", discoveryService.mapCityToLumaDiscoverId("San Francisco"))
        assertEquals("discplace-Izx1rQVSh8njYpP", discoveryService.mapCityToLumaDiscoverId("New York"))
        assertEquals("discplace-Cx3JMS6vXKAbhV5", discoveryService.mapCityToLumaDiscoverId("Toronto"))
    }
}
