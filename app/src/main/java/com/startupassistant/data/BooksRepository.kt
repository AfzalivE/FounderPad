package com.startupassistant.data

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToLong

interface BooksRepository {
    val transactions: StateFlow<List<Transaction>>
    fun add(date: LocalDate, description: String, amountCents: Long, category: TxCategory? = null): Transaction
    fun summary(burnMonths: Int = 3, today: LocalDate = LocalDate.now()): BooksSummary
}

private val rules: List<Pair<Regex, TxCategory>> = listOf(
    Regex("\\b(safe|seed|angel|investment|grant)\\b") to TxCategory.FUNDING,
    Regex("\\b(payroll|salary|gusto|deel|contractor)\\b") to TxCategory.PAYROLL,
    Regex("\\b(aws|gcp|azure|github|figma|notion|slack|vercel|google workspace|saas)\\b") to TxCategory.SOFTWARE,
    Regex("\\b(rent|cowork\\w*|wework|office|desks?)\\b") to TxCategory.RENT,
    Regex("\\b(ads?|marketing|sponsor\\w*|linkedin)\\b") to TxCategory.MARKETING,
    Regex("\\b(lawyer|legal|incorporation|stripe atlas|clerky|filing)\\b") to TxCategory.LEGAL,
    Regex("\\b(flights?|hotel|uber|lyft|travel|airbnb)\\b") to TxCategory.TRAVEL,
    Regex("\\b(invoice|customer|stripe payout|revenue)\\b") to TxCategory.REVENUE,
)

/**
 * Picks a category from keywords in the description. Money in with no match
 * counts as revenue. Money out with no match counts as other.
 */
fun categorize(description: String, amountCents: Long): TxCategory {
    val text = description.lowercase()
    for ((pattern, category) in rules) {
        if (category == TxCategory.REVENUE && amountCents < 0) continue
        if (pattern.containsMatchIn(text)) return category
    }
    return if (amountCents > 0) TxCategory.REVENUE else TxCategory.OTHER
}

/**
 * Mock bookkeeping store. There is no free bookkeeping API that works without
 * a company account, so transactions live in memory and reset on restart.
 */
class InMemoryBooksRepository(seed: List<Transaction> = seedTransactions()) : BooksRepository {

    private val _transactions = MutableStateFlow(seed.sortedByDescending { it.date })
    override val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()
    private var nextId = seed.size + 1

    @Synchronized
    override fun add(date: LocalDate, description: String, amountCents: Long, category: TxCategory?): Transaction {
        require(description.isNotBlank()) { "description is required" }
        require(amountCents != 0L) { "amount must not be zero" }
        val tx = Transaction(
            id = "t${nextId++}",
            date = date,
            description = description.trim(),
            amountCents = amountCents,
            category = category ?: categorize(description, amountCents),
        )
        _transactions.update { (it + tx).sortedByDescending { t -> t.date } }
        return tx
    }

    override fun summary(burnMonths: Int, today: LocalDate): BooksSummary {
        val txs = _transactions.value
        val cash = txs.sumOf { it.amountCents }
        val expensesByCategory = txs.filter { it.amountCents < 0 }
            .groupBy { it.category }
            .mapValues { (_, list) -> -list.sumOf { it.amountCents } }

        val months = txs.groupBy { YearMonth.from(it.date) }
            .toSortedMap()
            .map { (month, list) ->
                MonthSummary(
                    month = month.toString(),
                    // Funding is money in, but not operating income, so it does not reduce burn.
                    incomeCents = list.filter { it.amountCents > 0 && it.category != TxCategory.FUNDING }.sumOf { it.amountCents },
                    expenseCents = -list.filter { it.amountCents < 0 }.sumOf { it.amountCents },
                )
            }

        // The current month is not complete, so it would make burn look too low.
        val current = YearMonth.from(today).toString()
        val complete = months.filter { it.month < current }
        val recent = complete.ifEmpty { months }.takeLast(burnMonths)
        val burn = if (recent.isEmpty()) 0L else (recent.sumOf { -it.netCents }.toDouble() / recent.size).roundToLong()
        val runway = if (burn > 0) (cash.toDouble() / burn * 10).roundToLong() / 10.0 else null

        return BooksSummary(months, expensesByCategory, burn, cash, runway)
    }
}

/** Three months of plausible early-stage transactions, ending last month. */
fun seedTransactions(today: LocalDate = LocalDate.now()): List<Transaction> {
    data class S(val day: Int, val description: String, val usd: Long, val category: TxCategory)

    val monthly = listOf(
        listOf(
            S(2, "Pre-seed SAFE from angel investors", 250_000, TxCategory.FUNDING),
            S(3, "Stripe Atlas incorporation", -500, TxCategory.LEGAL),
            S(5, "Google Workspace", -36, TxCategory.SOFTWARE),
            S(8, "Coworking desks (2)", -900, TxCategory.RENT),
            S(15, "AWS", -120, TxCategory.SOFTWARE),
            S(28, "Contractor payment, design", -3_000, TxCategory.PAYROLL),
        ),
        listOf(
            S(1, "Coworking desks (2)", -900, TxCategory.RENT),
            S(5, "Google Workspace", -36, TxCategory.SOFTWARE),
            S(9, "Startup lawyer, option plan", -2_500, TxCategory.LEGAL),
            S(12, "Customer invoice #1001", 1_200, TxCategory.REVENUE),
            S(15, "AWS", -180, TxCategory.SOFTWARE),
            S(20, "Flight to customer visit", -450, TxCategory.TRAVEL),
            S(28, "Payroll", -9_000, TxCategory.PAYROLL),
        ),
        listOf(
            S(1, "Coworking desks (3)", -1_350, TxCategory.RENT),
            S(5, "Google Workspace", -54, TxCategory.SOFTWARE),
            S(10, "LinkedIn ads", -800, TxCategory.MARKETING),
            S(14, "Customer invoice #1002", 2_400, TxCategory.REVENUE),
            S(15, "AWS", -240, TxCategory.SOFTWARE),
            S(18, "Customer invoice #1003", 1_200, TxCategory.REVENUE),
            S(28, "Payroll", -13_500, TxCategory.PAYROLL),
        ),
    )
    var id = 1
    return monthly.flatMapIndexed { i, seeds ->
        val month = YearMonth.from(today).minusMonths((monthly.size - i).toLong())
        seeds.map { s -> Transaction("t${id++}", month.atDay(s.day), s.description, s.usd * 100, s.category) }
    }
}
