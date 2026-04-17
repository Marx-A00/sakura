package com.sakura.data.workout

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek

/**
 * Maps days of the week to workout template IDs.
 * Days not in the map are rest days.
 *
 * Persisted as JSON in DataStore: {"1":"uuid-1","4":"uuid-2"}
 * Keys are DayOfWeek.value (1=Monday..7=Sunday).
 */
data class WorkoutSchedule(
    val assignments: Map<DayOfWeek, String> = emptyMap()
) {
    /** Get the template ID assigned to a day, or null (rest day). */
    fun templateIdFor(day: DayOfWeek): String? = assignments[day]

    /** Set or clear a day's assignment. */
    fun withDay(day: DayOfWeek, templateId: String?): WorkoutSchedule {
        val mutable = assignments.toMutableMap()
        if (templateId != null) mutable[day] = templateId else mutable.remove(day)
        return copy(assignments = mutable)
    }

    fun toJson(): String {
        val map = assignments.map { (day, id) -> day.value.toString() to id }.toMap()
        return Json.encodeToString(SerializableSchedule(map))
    }

    companion object {
        fun fromJson(json: String): WorkoutSchedule {
            if (json.isBlank()) return WorkoutSchedule()
            return try {
                val raw = Json.decodeFromString<SerializableSchedule>(json)
                val map = raw.entries.mapNotNull { (key, id) ->
                    val dayValue = key.toIntOrNull() ?: return@mapNotNull null
                    if (dayValue !in 1..7) return@mapNotNull null
                    DayOfWeek.of(dayValue) to id
                }.toMap()
                WorkoutSchedule(map)
            } catch (_: Exception) {
                WorkoutSchedule()
            }
        }
    }
}

@Serializable
private data class SerializableSchedule(val entries: Map<String, String>)
