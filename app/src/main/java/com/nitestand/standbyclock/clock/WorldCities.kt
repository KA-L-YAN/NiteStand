package com.nitestand.standbyclock.clock

import java.time.ZoneId

/** A selectable world-clock location. Names are English place names; zones are IANA ids. */
data class WorldCity(val key: String, val name: String, val zoneId: String) {
    val zone: ZoneId get() = ZoneId.of(zoneId)
}

object WorldCities {
    /** Key meaning "use the phone's current time zone". */
    const val LOCAL = ""

    /** Key meaning "no secondary time zone". */
    const val NONE = "none"

    val all: List<WorldCity> = listOf(
        WorldCity("hyderabad", "Hyderabad", "Asia/Kolkata"),
        WorldCity("mumbai", "Mumbai", "Asia/Kolkata"),
        WorldCity("delhi", "New Delhi", "Asia/Kolkata"),
        WorldCity("bengaluru", "Bengaluru", "Asia/Kolkata"),
        WorldCity("dubai", "Dubai", "Asia/Dubai"),
        WorldCity("singapore", "Singapore", "Asia/Singapore"),
        WorldCity("hongkong", "Hong Kong", "Asia/Hong_Kong"),
        WorldCity("tokyo", "Tokyo", "Asia/Tokyo"),
        WorldCity("seoul", "Seoul", "Asia/Seoul"),
        WorldCity("sydney", "Sydney", "Australia/Sydney"),
        WorldCity("auckland", "Auckland", "Pacific/Auckland"),
        WorldCity("moscow", "Moscow", "Europe/Moscow"),
        WorldCity("berlin", "Berlin", "Europe/Berlin"),
        WorldCity("paris", "Paris", "Europe/Paris"),
        WorldCity("london", "London", "Europe/London"),
        WorldCity("johannesburg", "Johannesburg", "Africa/Johannesburg"),
        WorldCity("saopaulo", "São Paulo", "America/Sao_Paulo"),
        WorldCity("newyork", "New York", "America/New_York"),
        WorldCity("toronto", "Toronto", "America/Toronto"),
        WorldCity("chicago", "Chicago", "America/Chicago"),
        WorldCity("denver", "Denver", "America/Denver"),
        WorldCity("sanfrancisco", "San Francisco", "America/Los_Angeles"),
        WorldCity("honolulu", "Honolulu", "Pacific/Honolulu"),
        WorldCity("utc", "UTC", "UTC"),
    )

    private val byKey = all.associateBy { it.key }

    fun find(key: String): WorldCity? = byKey[key]

    /** Human label for a zone without a curated city, e.g. "Asia/Kolkata" → "Kolkata". */
    fun labelForZone(zone: ZoneId): String =
        zone.id.substringAfterLast('/').replace('_', ' ').ifBlank { zone.id }
}
