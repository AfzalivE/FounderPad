package com.startupassistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HiringRepositoryTest {
    private val repo = MockHiringRepository()

    @Test
    fun scalesSalaryByLevelAndLocation() {
        val mid = repo.salary("founding-engineer", Level.MID, "San Francisco")
        assertEquals(165_000, mid.medianUsd)
        assertTrue(repo.salary("founding-engineer", Level.SENIOR, "San Francisco").medianUsd > mid.medianUsd)
        assertTrue(repo.salary("founding-engineer", Level.MID, "Toronto").medianUsd < mid.medianUsd)
        assertTrue(mid.lowUsd < mid.medianUsd && mid.highUsd > mid.medianUsd)
    }

    @Test
    fun unknownLocationUsesDefaultMultiplier() {
        assertEquals(112_000, repo.salary("product-designer", Level.MID, "Lisbon").medianUsd)
    }

    @Test
    fun unknownRoleThrows() {
        assertThrows(UnknownRoleException::class.java) { repo.salary("ceo", Level.MID, "Remote") }
    }

    @Test
    fun jobPostContainsRolePitchAndPay() {
        val post = repo.draftJobPost("growth-marketer", Level.SENIOR, "Acme", "We help bakeries sell online.", "Remote")
        assertEquals("Senior Growth Marketer at Acme", post.title)
        assertTrue(post.body.contains("Location: Remote"))
        assertTrue(post.body.contains("We help bakeries sell online."))
        assertTrue(post.body.contains(MockHiringRepository.usd(post.salary.lowUsd)))
    }

    @Test
    fun filtersCandidates() {
        val c = repo.candidates("founding-engineer", remoteOnly = true)
        assertTrue(c.isNotEmpty())
        assertTrue(c.all { "founding-engineer" in it.roleIds && it.openToRemote })
    }
}
