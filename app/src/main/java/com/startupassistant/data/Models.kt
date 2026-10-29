package com.startupassistant.data

import java.time.LocalDate
import java.time.LocalDateTime

enum class EventCategory(val label: String) {
    NETWORKING("Find people"),
    FUNDING("Funding"),
    OFFICE_SPACE("Office space"),
    WORKSHOP("Workshops"),
}

data class StartupEvent(
    val id: String,
    val title: String,
    val description: String,
    val category: EventCategory,
    val city: String,
    val online: Boolean,
    val startsAt: LocalDateTime,
    val durationHours: Int,
    val host: String,
    val priceUsd: Int, // 0 = free
    val tags: List<String>,
)

data class EventQuery(
    val text: String? = null,
    val category: EventCategory? = null,
    val city: String? = null,
    val onlineOnly: Boolean = false,
    val freeOnly: Boolean = false,
)

enum class Level { JUNIOR, MID, SENIOR }

data class RoleTemplate(
    val id: String,
    val title: String,
    val summary: String,
    val responsibilities: List<String>,
    val skills: List<String>,
)

data class SalaryBenchmark(
    val roleId: String,
    val level: Level,
    val location: String,
    val lowUsd: Int,
    val medianUsd: Int,
    val highUsd: Int,
    val equityPercentLow: Double,
    val equityPercentHigh: Double,
)

data class Candidate(
    val id: String,
    val name: String,
    val headline: String,
    val roleIds: List<String>,
    val level: Level,
    val location: String,
    val openToRemote: Boolean,
    val skills: List<String>,
    val yearsExperience: Int,
)

data class JobPost(val title: String, val body: String, val salary: SalaryBenchmark)

enum class TxCategory { REVENUE, FUNDING, PAYROLL, SOFTWARE, RENT, MARKETING, LEGAL, TRAVEL, OTHER }

data class Transaction(
    val id: String,
    val date: LocalDate,
    val description: String,
    val amountCents: Long, // positive = money in, negative = money out
    val category: TxCategory,
)

data class MonthSummary(val month: String, val incomeCents: Long, val expenseCents: Long) {
    val netCents: Long get() = incomeCents - expenseCents
}

data class BooksSummary(
    val months: List<MonthSummary>,
    val expensesByCategory: Map<TxCategory, Long>,
    val averageMonthlyBurnCents: Long,
    val cashBalanceCents: Long,
    val runwayMonths: Double?, // null = not burning cash
)
