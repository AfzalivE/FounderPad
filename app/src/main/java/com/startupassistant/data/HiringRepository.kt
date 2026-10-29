package com.startupassistant.data

import com.startupassistant.data.Level.JUNIOR
import com.startupassistant.data.Level.MID
import com.startupassistant.data.Level.SENIOR
import kotlin.math.roundToInt

interface HiringRepository {
    fun roles(): List<RoleTemplate>
    fun salary(roleId: String, level: Level, location: String): SalaryBenchmark
    fun candidates(roleId: String? = null, level: Level? = null, remoteOnly: Boolean = false): List<Candidate>
    fun draftJobPost(roleId: String, level: Level, companyName: String, companyPitch: String, location: String): JobPost
    val checklist: List<String>
}

class UnknownRoleException(roleId: String) : IllegalArgumentException("Unknown role: $roleId")

/**
 * Mock hiring data. There is no free public API for salary data or candidate
 * sourcing, so this uses static benchmarks and a small candidate pool.
 */
class MockHiringRepository : HiringRepository {

    override fun roles() = roleTemplates

    private fun role(roleId: String) = roleTemplates.find { it.id == roleId } ?: throw UnknownRoleException(roleId)

    override fun salary(roleId: String, level: Level, location: String): SalaryBenchmark {
        role(roleId)
        val loc = locationMultiplier[location.trim().lowercase()] ?: DEFAULT_LOCATION_MULTIPLIER
        val median = roundToThousand(baseMedians.getValue(roleId) * levelMultiplier.getValue(level) * loc)
        val (eqLow, eqHigh) = equityRange.getValue(level)
        return SalaryBenchmark(
            roleId = roleId,
            level = level,
            location = location,
            lowUsd = roundToThousand(median * 0.85),
            medianUsd = median,
            highUsd = roundToThousand(median * 1.15),
            equityPercentLow = eqLow,
            equityPercentHigh = eqHigh,
        )
    }

    override fun candidates(roleId: String?, level: Level?, remoteOnly: Boolean) = allCandidates
        .filter { roleId == null || roleId in it.roleIds }
        .filter { level == null || it.level == level }
        .filter { !remoteOnly || it.openToRemote }
        .sortedByDescending { it.yearsExperience }

    override fun draftJobPost(
        roleId: String,
        level: Level,
        companyName: String,
        companyPitch: String,
        location: String,
    ): JobPost {
        val role = role(roleId)
        val salary = salary(roleId, level, location)
        val levelLabel = level.name.lowercase().replaceFirstChar { it.uppercase() }
        val title = "$levelLabel ${role.title} at $companyName"
        val body = buildString {
            appendLine(title)
            appendLine()
            appendLine("Location: $location")
            appendLine("Pay: ${usd(salary.lowUsd)} to ${usd(salary.highUsd)} base, plus ${salary.equityPercentLow}% to ${salary.equityPercentHigh}% equity")
            appendLine()
            appendLine("About us")
            appendLine(companyPitch.trim().ifEmpty { "$companyName is an early-stage startup." })
            appendLine()
            appendLine("The role")
            appendLine(role.summary)
            appendLine()
            appendLine("What you will do")
            role.responsibilities.forEach { appendLine("- $it") }
            appendLine()
            appendLine("What we look for")
            role.skills.forEach { appendLine("- $it") }
            appendLine("- You like to work in a small team where things change quickly")
            appendLine()
            appendLine("How to apply")
            append("Send a short note about something you built or did that you are proud of.")
        }
        return JobPost(title, body, salary)
    }

    override val checklist = listOf(
        "Write down what the first hire must do in their first 90 days",
        "Pick a salary and equity range before you talk to candidates",
        "Set up payroll and an employment contract template",
        "Decide on an equity plan and get board approval for option grants",
        "Prepare a short, paid work trial",
        "Check references for every finalist",
        "Plan the first week of onboarding",
    )

    companion object {
        private const val DEFAULT_LOCATION_MULTIPLIER = 0.8

        private fun roundToThousand(n: Double) = (n / 1000).roundToInt() * 1000
        fun usd(n: Int) = "$" + "%,d".format(n)

        val roleTemplates = listOf(
            RoleTemplate(
                "founding-engineer", "Founding Engineer",
                "Build the first version of the product and set up the engineering foundations.",
                listOf(
                    "Design, build and ship the core product end to end",
                    "Choose the tech stack and set up deploys, monitoring and testing",
                    "Talk to early customers and turn feedback into features",
                    "Help hire and onboard the next engineers",
                ),
                listOf("Kotlin", "TypeScript", "Cloud infrastructure", "Product sense"),
            ),
            RoleTemplate(
                "product-designer", "Product Designer",
                "Own the user experience, from research to polished UI.",
                listOf(
                    "Run user research and usability tests",
                    "Design flows, wireframes and high-fidelity UI",
                    "Create and maintain a simple design system",
                    "Work closely with engineering to ship quickly",
                ),
                listOf("Figma", "User research", "Prototyping", "Visual design"),
            ),
            RoleTemplate(
                "growth-marketer", "Growth Marketer",
                "Find repeatable channels that bring in users and customers.",
                listOf(
                    "Run experiments across paid, content and community channels",
                    "Set up analytics and report on funnel metrics",
                    "Write landing pages, emails and launch posts",
                    "Own the launch plan for new features",
                ),
                listOf("Analytics", "Copywriting", "SEO", "Paid acquisition"),
            ),
            RoleTemplate(
                "founding-sales", "Founding Account Executive",
                "Close the first customers and build a repeatable sales process.",
                listOf(
                    "Prospect, run demos and close early deals",
                    "Write the first sales playbook",
                    "Feed customer insights back to the product team",
                    "Set up the CRM and pipeline reporting",
                ),
                listOf("B2B sales", "Discovery calls", "Negotiation", "CRM"),
            ),
            RoleTemplate(
                "operations-generalist", "Operations Generalist",
                "Keep the company running: finance, people ops, office and tools.",
                listOf(
                    "Run payroll, bookkeeping handoffs and vendor payments",
                    "Coordinate hiring logistics and onboarding",
                    "Manage office space, equipment and software accounts",
                    "Track company metrics and board reporting",
                ),
                listOf("Operations", "Finance basics", "Hiring coordination", "Organization"),
            ),
        )

        // Median base salary in USD for a mid-level hire in a high-cost city.
        private val baseMedians = mapOf(
            "founding-engineer" to 165_000,
            "product-designer" to 140_000,
            "growth-marketer" to 125_000,
            "founding-sales" to 120_000,
            "operations-generalist" to 95_000,
        )

        private val levelMultiplier = mapOf(JUNIOR to 0.75, MID to 1.0, SENIOR to 1.3)

        // Equity ranges (percent of company) typical for early hires.
        private val equityRange = mapOf(JUNIOR to (0.1 to 0.4), MID to (0.25 to 0.75), SENIOR to (0.5 to 1.5))

        private val locationMultiplier = mapOf(
            "san francisco" to 1.0,
            "new york" to 0.97,
            "boston" to 0.9,
            "seattle" to 0.95,
            "toronto" to 0.7,
            "london" to 0.75,
            "berlin" to 0.65,
            "remote" to 0.85,
        )

        private val allCandidates = listOf(
            Candidate("c1", "Maya Chen", "Full-stack engineer, ex-fintech startup", listOf("founding-engineer"), SENIOR, "San Francisco", true, listOf("TypeScript", "Postgres", "AWS"), 8),
            Candidate("c2", "Jordan Okafor", "Backend engineer who loves zero-to-one", listOf("founding-engineer"), MID, "Toronto", true, listOf("Python", "Django", "GCP"), 4),
            Candidate("c3", "Priya Raman", "Mobile and web engineer", listOf("founding-engineer"), MID, "New York", false, listOf("Kotlin", "React", "Firebase"), 5),
            Candidate("c4", "Sam Rivera", "New grad, two startup internships", listOf("founding-engineer"), JUNIOR, "Boston", true, listOf("TypeScript", "Go"), 1),
            Candidate("c5", "Lena Fischer", "Product designer, B2B SaaS", listOf("product-designer"), SENIOR, "Berlin", true, listOf("Figma", "Design systems", "User research"), 9),
            Candidate("c6", "Alex Kim", "Designer who also writes front-end code", listOf("product-designer", "founding-engineer"), MID, "Seattle", true, listOf("Figma", "Compose", "Prototyping"), 4),
            Candidate("c7", "Hannah Brooks", "Growth lead, grew a consumer app to 1M users", listOf("growth-marketer"), SENIOR, "New York", true, listOf("Paid acquisition", "Analytics", "Lifecycle email"), 7),
            Candidate("c8", "Diego Santos", "Content and SEO marketer", listOf("growth-marketer"), MID, "Remote", true, listOf("SEO", "Copywriting", "Webflow"), 3),
            Candidate("c9", "Grace Liu", "Enterprise AE, first sales hire twice", listOf("founding-sales"), SENIOR, "San Francisco", false, listOf("B2B sales", "Negotiation", "Salesforce"), 10),
            Candidate("c10", "Tom Becker", "SDR turned AE at a seed-stage startup", listOf("founding-sales"), MID, "London", true, listOf("Discovery calls", "HubSpot"), 3),
            Candidate("c11", "Nia Thompson", "Startup operations and finance generalist", listOf("operations-generalist"), MID, "Boston", true, listOf("Operations", "QuickBooks", "Hiring coordination"), 5),
            Candidate("c12", "Ravi Patel", "Chief of staff at a Series A company", listOf("operations-generalist", "founding-sales"), SENIOR, "Toronto", true, listOf("Operations", "Finance basics", "B2B sales"), 8),
        )
    }
}
