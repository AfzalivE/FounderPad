package com.startupassistant.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified data model representing a Cofounder Meetup, VC Pitch Event, or Local Incubator Resource.
 */
data class FounderEvent(
    val id: String,
    val title: String,
    val category: String, // "Cofounder_Meetup", "VC_Backer", or "Local_Resource"
    val dateAndTime: String,
    val locationOrVenue: String,
    val hostOrOrganizer: String,
    val registrationUrl: String,
    val matchRationale: String,
    val sourcePlatform: String, // "Luma", "Meetup", "Eventbrite", or "Google Search Grounded"
)

class FounderEventDiscoveryService {

    /**
     * Primary entry point for FounderPad Step 4:
     * Runs both the unauthenticated Luma JSON feed and Firebase AI Logic Google Search Grounding,
     * merging them into a deduplicated list for the UI.
     */
    suspend fun discoverFounderEvents(
        targetCity: String,
        coreSkill: String = "",
        missionAndValueProp: String = "",
    ): List<FounderEvent> = withContext(Dispatchers.IO) {
        val combinedEvents = mutableListOf<FounderEvent>()

        // 1. Fetch live grounded events across lu.ma, Meetup, and Eventbrite via Firebase AI Logic
        val groundedEvents = runCatching {
            fetchGroundedEventsViaFirebaseAI(targetCity, coreSkill, missionAndValueProp)
        }.getOrDefault(emptyList())
        combinedEvents.addAll(groundedEvents)

        // 2. Fetch direct JSON from Luma's unauthenticated public discovery endpoint
        val lumaDirectEvents = runCatching {
            fetchUnauthenticatedLumaEvents(targetCity, coreSkill)
        }.getOrDefault(emptyList())
        combinedEvents.addAll(lumaDirectEvents)

        // Deduplicate by URL or title
        combinedEvents.distinctBy { it.registrationUrl.lowercase().ifBlank { it.title.lowercase() } }
    }

    // =========================================================================================
    // ENGINE 1: Firebase AI Logic + Google Search Grounding (lu.ma, Meetup, Eventbrite, SBA/VC)
    // =========================================================================================
    suspend fun fetchGroundedEventsViaFirebaseAI(
        targetCity: String,
        coreSkill: String,
        missionAndValueProp: String,
    ): List<FounderEvent> = withContext(Dispatchers.IO) {
        // Initialize Firebase AI Logic with Google Search Grounding enabled
        val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-3.8-flash", // Or "gemini-2.5-flash" depending on your BOM version
                tools = listOf(Tool.googleSearch()),
            )

        val prompt = """
            You are a startup launchpad scout. Search live listings on lu.ma, meetup.com, eventbrite.com, 
            and local SBA/SCORE or Venture Capital calendars in or near "$targetCity".
            
            Founder Background Skill: "$coreSkill"
            Target Small Business Value Proposition: "$missionAndValueProp"
            
            Find 6 real upcoming events or active local resources split across these 3 categories:
            1. "Cofounder_Meetup" (Founder mixers, hackathons, technical/business cofounder networking)
            2. "VC_Backer" (Angel pitch nights, pre-seed/seed office hours, micro-grant or incubator demo days)
            3. "Local_Resource" (Small Business Development Center workshops, SCORE mentorship, chamber/trade meetups)
            
            Respond ONLY with a raw JSON array (no markdown fences) where each object has:
            {
               "id": "unique-slug",
               "title": "Exact event or organization name",
               "category": "Cofounder_Meetup | VC_Backer | Local_Resource",
               "dateAndTime": "Upcoming date/time or recurring schedule",
               "locationOrVenue": "Venue name and city",
               "hostOrOrganizer": "Host name",
               "registrationUrl": "Direct URL to lu.ma, meetup.com, eventbrite, or official page",
               "matchRationale": "1 punchy sentence on why this helps launch their specific value prop",
               "sourcePlatform": "Luma | Meetup | Eventbrite | Web"
            }
        """.trimIndent()

        val response = generativeModel.generateContent(content { text(prompt) })
        val rawText = response.text ?: return@withContext emptyList()

        // Extract verified grounding URLs from Google Search chunks as backup links
        val groundedUrls = response.candidates.firstOrNull()
            ?.groundingMetadata
            ?.groundingChunks
            ?.mapNotNull { it.web?.uri }
            ?: emptyList()

        parseGroundedJsonToEvents(rawText, groundedUrls)
    }

    // =========================================================================================
    // ENGINE 2: Unauthenticated lu.ma Public Discovery JSON Endpoint (Zero API Key Required)
    // =========================================================================================
    suspend fun fetchUnauthenticatedLumaEvents(
        targetCity: String,
        keywordFilter: String = "",
    ): List<FounderEvent> = withContext(Dispatchers.IO) {
        val placeId = mapCityToLumaDiscoverId(targetCity)
        val encodedPlace = URLEncoder.encode(placeId, "UTF-8")
        val primaryEndpoint =
            "https://api.lu.ma/discover/get-paginated-events?discover_place_api_id=$encodedPlace&pagination_limit=25"
        val fallbackEndpoint = "https://api.lu.ma/discover/get-paginated-events?pagination_limit=25"

        val responseBody = fetchLumaJson(primaryEndpoint)
            ?: fetchLumaJson(fallbackEndpoint)
            ?: return@withContext emptyList()

        parseLumaEntriesJson(responseBody, targetCity, keywordFilter)
    }

    private fun fetchLumaJson(endpoint: String): String? {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FounderPad-Android/1.0")
            connectTimeout = 8000
            readTimeout = 8000
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            return null
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    internal fun parseLumaEntriesJson(
        responseBody: String,
        targetCity: String,
        keywordFilter: String = "",
    ): List<FounderEvent> {
        val rootJson = JSONObject(responseBody)
        val entries = rootJson.optJSONArray("entries") ?: return emptyList()

        val results = mutableListOf<FounderEvent>()

        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val eventObj = entry.optJSONObject("event") ?: continue
            val calendarObj = entry.optJSONObject("calendar")
            val geoInfo = eventObj.optJSONObject("geo_address_info")

            val apiId = eventObj.optString("api_id", "luma-$i")
            val title = eventObj.optString("name", "Untitled Luma Event")
            val startAt = eventObj.optString("start_at", "Upcoming")
            val urlSlug = eventObj.optString("url", "")
            val fullUrl = if (urlSlug.startsWith("http")) urlSlug else "https://lu.ma/$urlSlug"
            val cityLabel = geoInfo?.optString("city")?.takeIf { it.isNotBlank() }
                ?: geoInfo?.optString("city_state")?.takeIf { it.isNotBlank() }
                ?: targetCity.ifBlank { "San Francisco" }
            val venueLabel = geoInfo?.optString("city_state")?.takeIf { it.isNotBlank() }
                ?: geoInfo?.optString("address")?.takeIf { it.isNotBlank() }
                ?: cityLabel
            val hostName = calendarObj?.optString("name")?.takeIf { it.isNotBlank() }
                ?: entry.optJSONArray("hosts")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
                ?: "Community Host"

            // Categorize based on title keywords for FounderPad's 3 pillars
            val lowerTitle = title.lowercase()
            val category = when {
                lowerTitle.contains("vc") || lowerTitle.contains("pitch") ||
                    lowerTitle.contains("investor") || lowerTitle.contains("demo day") ||
                    lowerTitle.contains("fund") -> "VC_Backer"
                lowerTitle.contains("cofounder") || lowerTitle.contains("founder") ||
                    lowerTitle.contains("mixer") || lowerTitle.contains("networking") ||
                    lowerTitle.contains("hackathon") || lowerTitle.contains("ai") ||
                    lowerTitle.contains("builder") -> "Cofounder_Meetup"
                else -> "Local_Resource"
            }

            results.add(
                FounderEvent(
                    id = apiId,
                    title = title,
                    category = category,
                    dateAndTime = startAt,
                    locationOrVenue = venueLabel,
                    hostOrOrganizer = hostName,
                    registrationUrl = fullUrl,
                    matchRationale = "Live community event on Luma near $cityLabel matching your launchpad.",
                    sourcePlatform = "Luma",
                ),
            )
        }

        // Prioritize events matching the founder's skill or startup/VC keywords
        return if (keywordFilter.isNotBlank()) {
            val tokens = keywordFilter.lowercase().split(" ").filter { it.length > 2 }
            results.sortedByDescending { ev ->
                tokens.count { token ->
                    ev.title.lowercase().contains(token) || ev.hostOrOrganizer.lowercase().contains(token)
                }
            }
        } else {
            results
        }
    }

    // =========================================================================================
    // Helpers: City Slug Mapper & Resilient JSON Parser
    // =========================================================================================
    internal fun mapCityToLumaDiscoverId(cityInput: String): String {
        val normalized = cityInput.lowercase()
        return when {
            normalized.contains("mountain view") || normalized.contains("sunnyvale") ||
                normalized.contains("san francisco") || normalized.contains("bay area") ||
                normalized.contains("palo alto") || normalized.contains("san jose") ||
                normalized == "sf" -> "discplace-BDj7GNbGlsF7Cka" // lu.ma/sf
            normalized.contains("los angeles") || normalized.contains("irvine") ||
                normalized.contains("orange county") || normalized.contains("santa monica") ||
                normalized == "la" -> "discplace-OgfEAh5KgfMzise" // lu.ma/la
            normalized.contains("new york") || normalized.contains("nyc") -> "discplace-Izx1rQVSh8njYpP" // lu.ma/nyc
            normalized.contains("toronto") -> "discplace-Cx3JMS6vXKAbhV5" // lu.ma/toronto
            normalized.contains("boston") -> "discplace-VWeZ1zUvnawYHMj" // lu.ma/boston
            normalized.contains("austin") -> "discplace-0tPy8KGz3xMycnt" // lu.ma/austin
            normalized.contains("seattle") -> "discplace-FQ4E58PeBMHGTKK" // lu.ma/seattle
            normalized.contains("london") -> "discplace-QCcNk3HXowOR97j" // lu.ma/london
            else -> "discplace-BDj7GNbGlsF7Cka" // Default to SF/Bay Area for the ADS 2026 demo
        }
    }

    internal fun parseGroundedJsonToEvents(
        rawText: String,
        fallbackGroundedUrls: List<String>,
    ): List<FounderEvent> {
        val cleanedJson = rawText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val startIndex = cleanedJson.indexOf('[')
        val endIndex = cleanedJson.lastIndexOf(']')
        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) return emptyList()

        val jsonArray = JSONArray(cleanedJson.substring(startIndex, endIndex + 1))
        val events = mutableListOf<FounderEvent>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val fallbackUrl = fallbackGroundedUrls.getOrNull(i) ?: "https://lu.ma/sf"
            events.add(
                FounderEvent(
                    id = obj.optString("id", "grounded-$i"),
                    title = obj.optString("title", "Founder Networking Event"),
                    category = obj.optString("category", "Cofounder_Meetup"),
                    dateAndTime = obj.optString("dateAndTime", "Upcoming"),
                    locationOrVenue = obj.optString("locationOrVenue", "Mountain View / Bay Area"),
                    hostOrOrganizer = obj.optString("hostOrOrganizer", "Local Startup Community"),
                    registrationUrl = obj.optString("registrationUrl", fallbackUrl).ifBlank { fallbackUrl },
                    matchRationale = obj.optString("matchRationale", "Matched via Google Search Grounding."),
                    sourcePlatform = obj.optString("sourcePlatform", "Google Search Grounded"),
                ),
            )
        }
        return events
    }
}

/**
 * Maps a [FounderEvent] from Luma / Firebase AI Logic Grounding into the existing
 * [StartupEvent] domain model used across [EventRepository], [com.startupassistant.StartupTools],
 * and the Compose UI.
 */
fun FounderEvent.toStartupEvent(
    referenceDate: LocalDate = LocalDate.now(),
    indexOffset: Int = 0,
): StartupEvent {
    val mappedCategory = when (category.trim()) {
        "VC_Backer" -> EventCategory.FUNDING
        "Cofounder_Meetup" -> EventCategory.NETWORKING
        "Local_Resource" -> if (
            title.contains("cowork", ignoreCase = true) ||
            title.contains("office", ignoreCase = true) ||
            title.contains("incubator", ignoreCase = true)
        ) {
            EventCategory.OFFICE_SPACE
        } else {
            EventCategory.WORKSHOP
        }
        else -> EventCategory.NETWORKING
    }

    val parsedStartsAt = runCatching {
        Instant.parse(dateAndTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }.getOrElse {
        referenceDate.plusDays((indexOffset % 10 + 1).toLong()).atTime(18, 0)
    }

    val isOnline = locationOrVenue.contains("online", ignoreCase = true) ||
        locationOrVenue.contains("virtual", ignoreCase = true)

    val platformTag = sourcePlatform.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    val pillarTag = category.lowercase().replace('_', '-')

    return StartupEvent(
        id = id.ifBlank { title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') },
        title = title,
        description = if (registrationUrl.isNotBlank()) {
            "$matchRationale ($sourcePlatform: $registrationUrl)"
        } else {
            matchRationale
        },
        category = mappedCategory,
        city = if (isOnline) "Online" else locationOrVenue.ifBlank { "San Francisco" },
        online = isOnline,
        startsAt = parsedStartsAt,
        durationHours = 2,
        host = hostOrOrganizer,
        priceUsd = 0,
        tags = listOfNotNull(
            platformTag.takeIf { it.isNotBlank() },
            pillarTag.takeIf { it.isNotBlank() },
        ),
        registrationUrl = registrationUrl,
        sourcePlatform = sourcePlatform,
    )
}
