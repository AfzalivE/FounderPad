package com.startupassistant.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRepositoryTest {
    private val today = LocalDate.of(2026, 1, 10)
    private val repo = MockEventRepository(today)

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
}
