package com.startupassistant

import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.startupassistant.data.EventCategory
import com.startupassistant.data.EventQuery
import com.startupassistant.data.Level
import com.startupassistant.data.MockHiringRepository.Companion.usd
import com.startupassistant.data.StartupEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// Tools for the in-app Gemini Nano agent and for the Gemini app (Connected
// Apps). Each KDoc is the prompt the model sees, so it says when to call the
// tool and what each argument means. Results are pre-formatted for people:
// names, dates, places and prices as text.

/** One upcoming event. */
@AppFunctionSerializable
data class EventInfo(
    /** Event name. */
    val title: String,
    /** Human-readable date and time, for example "Tue, Nov 3, 6:00 PM (3 h)". */
    val whenText: String,
    /** City, or "Online". */
    val where: String,
    /** Organizer name. */
    val host: String,
    /** "Free" or a price such as "$25". */
    val price: String,
    /** One of: people, funding, office, workshop. */
    val category: String,
    /** One-sentence summary. */
    val description: String,
)

/** Result of an event search. */
@AppFunctionSerializable
data class EventSearchResult(
    /** Matching events, soonest first. Empty when nothing matches. */
    val events: List<EventInfo>,
    /** Short note for the user, for example that the data is sample data. */
    val note: String,
)

/** Pay range for one role. */
@AppFunctionSerializable
data class SalaryInfo(
    /** Role name, for example "Founding Engineer". */
    val role: String,
    /** junior, mid or senior. */
    val level: String,
    /** Location used for the estimate. */
    val location: String,
    /** Base salary range, for example "$140,000 to $190,000". */
    val baseRange: String,
    /** Median base salary in US dollars. */
    val medianUsd: Int,
    /** Equity range, for example "0.25% to 0.75%". */
    val equityRange: String,
)

/** One candidate profile. */
@AppFunctionSerializable
data class CandidateInfo(
    /** Full name. */
    val name: String,
    /** One-line summary of the person. */
    val headline: String,
    /** junior, mid or senior. */
    val level: String,
    /** Where the person lives. */
    val location: String,
    /** True if the person will work remotely. */
    val openToRemote: Boolean,
    /** Years of work experience. */
    val yearsExperience: Int,
    /** Main skills. */
    val skills: List<String>,
)

/** A job post draft. */
@AppFunctionSerializable
data class JobPostDraft(
    /** Post title. */
    val title: String,
    /** Full post text, ready to paste into a job board. */
    val body: String,
)

/** Result of recording a transaction. */
@AppFunctionSerializable
data class TransactionResult(
    /** True if the transaction was saved. */
    val saved: Boolean,
    /** Category that was used, for example "software". */
    val category: String,
    /** Short message for the user. */
    val message: String,
)

/** Cash, burn and runway for the company. */
@AppFunctionSerializable
data class RunwayInfo(
    /** Cash in the bank, for example "$221,214". */
    val cash: String,
    /** Average monthly net burn over the last three complete months. */
    val monthlyBurn: String,
    /** Months of runway as text, for example "27.4 months", or "Not burning cash". */
    val runway: String,
    /** The largest expense categories with amounts, biggest first. */
    val topExpenses: List<String>,
)

@AppFunctionServiceEntryPoint(
    serviceName = "StartupAppFunctionService",
    appFunctionXmlFileName = "startup_app_functions",
)
abstract class StartupTools : AppFunctionService() {

    /**
     * Finds upcoming startup events where a founder can meet people (cofounders,
     * mentors), meet investors, find office space or learn skills. Call this when
     * the user asks about events, meetups, networking, pitching, fundraising
     * events, coworking or office space.
     *
     * @param topic Optional free text to match, for example "SAFE", "AI" or "cofounder".
     * @param category Optional. One of: "people", "funding", "office", "workshop".
     * @param city Optional city name, for example "Toronto". Use "Online" for online events.
     * @param freeOnly True to return only free events.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun find_events(
        topic: String? = null,
        category: String? = null,
        city: String? = null,
        freeOnly: Boolean = false,
    ): EventSearchResult {
        val online = city?.equals("online", ignoreCase = true) == true
        val events = AppGraph.events.search(
            EventQuery(
                text = topic,
                category = parseCategory(category),
                city = if (online) null else city,
                onlineOnly = online,
                freeOnly = freeOnly,
            ),
        )
        return EventSearchResult(
            events = events.take(MAX_RESULTS).map { it.toInfo() },
            note = "Sample events. A live event source is not connected yet.",
        )
    }

    /**
     * Estimates base salary and equity for an early startup hire. Call this
     * before writing a job post, or when the user asks what to pay someone.
     *
     * @param role One of: "founding-engineer", "product-designer", "growth-marketer",
     *   "founding-sales", "operations-generalist".
     * @param level One of: "junior", "mid", "senior". Use "mid" if the user does not say.
     * @param location City name, or "Remote".
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun get_salary_range(role: String, level: String = "mid", location: String = "Remote"): SalaryInfo {
        val roleId = resolveRole(role)
        val s = AppGraph.hiring.salary(roleId, parseLevel(level), location)
        return SalaryInfo(
            role = AppGraph.hiring.roles().first { it.id == roleId }.title,
            level = s.level.name.lowercase(),
            location = s.location,
            baseRange = "${usd(s.lowUsd)} to ${usd(s.highUsd)}",
            medianUsd = s.medianUsd,
            equityRange = "${s.equityPercentLow}% to ${s.equityPercentHigh}%",
        )
    }

    /**
     * Lists candidates who could fill a role at an early startup. Call this when
     * the user asks who they could hire, or wants to see people for a role.
     *
     * @param role One of: "founding-engineer", "product-designer", "growth-marketer",
     *   "founding-sales", "operations-generalist".
     * @param remoteOnly True to return only people who are open to remote work.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun find_candidates(role: String, remoteOnly: Boolean = false): List<CandidateInfo> =
        AppGraph.hiring.candidates(resolveRole(role), remoteOnly = remoteOnly).take(MAX_RESULTS).map {
            CandidateInfo(
                name = it.name,
                headline = it.headline,
                level = it.level.name.lowercase(),
                location = it.location,
                openToRemote = it.openToRemote,
                yearsExperience = it.yearsExperience,
                skills = it.skills,
            )
        }

    /**
     * Writes a job post for a role, with a fair salary and equity range already
     * filled in. Call this when the user wants to hire someone or asks for a job
     * description.
     *
     * @param role One of: "founding-engineer", "product-designer", "growth-marketer",
     *   "founding-sales", "operations-generalist".
     * @param companyName The name of the user's company.
     * @param companyPitch One or two sentences about what the company does. Empty if unknown.
     * @param level One of: "junior", "mid", "senior".
     * @param location City name, or "Remote".
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun draft_job_post(
        role: String,
        companyName: String,
        companyPitch: String = "",
        level: String = "mid",
        location: String = "Remote",
    ): JobPostDraft {
        val post = AppGraph.hiring.draftJobPost(resolveRole(role), parseLevel(level), companyName, companyPitch, location)
        return JobPostDraft(post.title, post.body)
    }

    /**
     * Records one income or expense in the company books. This changes data, so
     * only call it when the user clearly asks to add or log a transaction.
     *
     * @param description What the money was for, for example "AWS bill" or "Customer invoice #12".
     * @param amountUsd Amount in US dollars. Positive for money in, negative for money out.
     * @param date Date as YYYY-MM-DD. Empty for today.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun add_transaction(description: String, amountUsd: Double, date: String = ""): TransactionResult {
        val day = try {
            if (date.isBlank()) LocalDate.now() else LocalDate.parse(date)
        } catch (e: DateTimeParseException) {
            return TransactionResult(false, "", "Date must be YYYY-MM-DD.")
        }
        val cents = Math.round(amountUsd * 100)
        if (cents == 0L || description.isBlank()) {
            return TransactionResult(false, "", "A description and a non-zero amount are required.")
        }
        val tx = AppGraph.books.add(day, description, cents)
        val category = tx.category.name.lowercase()
        return TransactionResult(true, category, "Saved ${usd((kotlin.math.abs(cents) / 100).toInt())} as $category on $day.")
    }

    /**
     * Reports cash in the bank, monthly burn rate and months of runway, plus the
     * biggest expense categories. Call this when the user asks about money,
     * burn, runway, spending or how long the company can last.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun get_runway(): RunwayInfo {
        val s = AppGraph.books.summary()
        return RunwayInfo(
            cash = usd((s.cashBalanceCents / 100).toInt()),
            monthlyBurn = usd((s.averageMonthlyBurnCents / 100).toInt()),
            runway = s.runwayMonths?.let { "$it months" } ?: "Not burning cash",
            topExpenses = s.expensesByCategory.entries.sortedByDescending { it.value }.take(3)
                .map { "${it.key.name.lowercase()}: ${usd((it.value / 100).toInt())}" },
        )
    }

    private companion object {
        const val MAX_RESULTS = 5
        val whenFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a")

        fun StartupEvent.toInfo() = EventInfo(
            title = title,
            whenText = "${startsAt.format(whenFormat)} (${durationHours} h)",
            where = if (online) "Online" else city,
            host = host,
            price = if (priceUsd == 0) "Free" else "$$priceUsd",
            category = when (category) {
                EventCategory.NETWORKING -> "people"
                EventCategory.FUNDING -> "funding"
                EventCategory.OFFICE_SPACE -> "office"
                EventCategory.WORKSHOP -> "workshop"
            },
            description = description,
        )

        fun parseCategory(value: String?): EventCategory? = when (value?.trim()?.lowercase()) {
            "people", "networking", "cofounder" -> EventCategory.NETWORKING
            "funding", "investors", "fundraising" -> EventCategory.FUNDING
            "office", "office-space", "office space", "coworking" -> EventCategory.OFFICE_SPACE
            "workshop", "learning" -> EventCategory.WORKSHOP
            else -> null
        }

        fun parseLevel(value: String): Level =
            Level.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: Level.MID

        /** Accepts an id or a loose name ("engineer", "designer", "sales") from the model. */
        fun resolveRole(value: String): String {
            val v = value.trim().lowercase()
            val roles = AppGraph.hiring.roles()
            roles.firstOrNull { it.id == v || it.title.lowercase() == v }?.let { return it.id }
            return when {
                "engineer" in v || "developer" in v || "cto" in v -> "founding-engineer"
                "design" in v -> "product-designer"
                "market" in v || "growth" in v -> "growth-marketer"
                "sales" in v || "account" in v -> "founding-sales"
                else -> "operations-generalist"
            }
        }
    }
}
