package com.startupassistant.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BooksRepositoryTest {

    private val seed = listOf(
        Transaction("a", LocalDate.of(2026, 1, 2), "SAFE", 10_000_00, TxCategory.FUNDING),
        Transaction("b", LocalDate.of(2026, 1, 15), "Payroll", -2_000_00, TxCategory.PAYROLL),
        Transaction("c", LocalDate.of(2026, 2, 15), "Payroll", -2_000_00, TxCategory.PAYROLL),
        Transaction("d", LocalDate.of(2026, 2, 20), "Invoice", 1_000_00, TxCategory.REVENUE),
    )
    private val march5 = LocalDate.of(2026, 3, 5)

    @Test
    fun categorizesByKeywords() {
        assertEquals(TxCategory.SOFTWARE, categorize("AWS invoice", -120_00))
        assertEquals(TxCategory.FUNDING, categorize("Seed round SAFE", 50_000_00))
        assertEquals(TxCategory.PAYROLL, categorize("Gusto payroll", -9_000_00))
        assertEquals(TxCategory.RENT, categorize("WeWork desk", -500_00))
        assertEquals(TxCategory.TRAVEL, categorize("Flight to Toronto", -400_00))
        assertEquals(TxCategory.REVENUE, categorize("Customer invoice #12", 1_000_00))
        assertEquals(TxCategory.REVENUE, categorize("Refund from vendor", 20_00))
        assertEquals(TxCategory.OTHER, categorize("Coffee", -5_00))
    }

    @Test
    fun computesCashBurnAndRunwayWithoutCountingFundingAsIncome() {
        val s = InMemoryBooksRepository(seed).summary(today = march5)
        assertEquals(7_000_00L, s.cashBalanceCents)
        assertEquals(listOf("2026-01", "2026-02"), s.months.map { it.month })
        assertEquals(0L, s.months[0].incomeCents)
        assertEquals(-1_000_00L, s.months[1].netCents)
        assertEquals(1_500_00L, s.averageMonthlyBurnCents)
        assertEquals(4.7, s.runwayMonths!!, 0.0)
        assertEquals(mapOf(TxCategory.PAYROLL to 4_000_00L), s.expensesByCategory)
    }

    @Test
    fun leavesCurrentMonthOutOfBurn() {
        val repo = InMemoryBooksRepository(seed)
        repo.add(LocalDate.of(2026, 3, 1), "AWS", -1_00)
        val s = repo.summary(today = march5)
        assertEquals(3, s.months.size)
        assertEquals(1_500_00L, s.averageMonthlyBurnCents)
    }

    @Test
    fun runwayIsNullWhenNotBurning() {
        assertNull(InMemoryBooksRepository(listOf(seed[3])).summary(today = march5).runwayMonths)
    }

    @Test
    fun addAutoCategorizesAndRejectsBadInput() {
        val repo = InMemoryBooksRepository(seed)
        val t = repo.add(LocalDate.of(2026, 3, 1), "Figma", -45_00)
        assertEquals(TxCategory.SOFTWARE, t.category)
        assertEquals(t, repo.transactions.value.first())
        assertThrows(IllegalArgumentException::class.java) { repo.add(march5, " ", -1) }
        assertThrows(IllegalArgumentException::class.java) { repo.add(march5, "x", 0) }
    }

    @Test
    fun seedCoversLastThreeMonths() {
        val s = InMemoryBooksRepository(seedTransactions(march5)).summary(today = march5)
        assertEquals(listOf("2025-12", "2026-01", "2026-02"), s.months.map { it.month })
        assertTrue(s.runwayMonths!! > 0)
    }
}
