package com.sakura.orgengine

import java.time.LocalDate

/**
 * Deserializes org-mode text into OrgFile models.
 *
 * Uses a sequential state machine that walks lines top-to-bottom.
 * Unrecognized lines are silently skipped (graceful degradation).
 *
 * Parse modes:
 *   FOOD    -- expects meal group headings and food entry lines
 *   WORKOUT -- expects a workout heading and exercise entry lines
 */
object OrgParser {

    enum class ParseMode { FOOD, WORKOUT }

    /**
     * Parse org-mode text into an OrgFile model.
     *
     * @param content Raw org file text (may be empty)
     * @param mode    FOOD or WORKOUT — determines how to interpret level-2 headings and entries
     */
    fun parse(content: String, mode: ParseMode): OrgFile {
        if (content.isBlank()) return OrgFile(sections = emptyList())

        val sections = mutableListOf<OrgDateSection>()

        // State machine accumulators
        var currentDate: LocalDate? = null
        var currentMeals = mutableListOf<OrgMealGroup>()
        var currentMealLabel: String? = null
        var currentMealEntries = mutableListOf<OrgFoodEntry>()
        var currentExercises = mutableListOf<OrgExerciseEntry>()

        /**
         * Flush the current meal group into the meals list (if any entries accumulated).
         * Called when we see a new meal heading or a new date heading.
         */
        fun flushCurrentMeal() {
            val label = currentMealLabel
            if (label != null && currentMealEntries.isNotEmpty()) {
                currentMeals.add(OrgMealGroup(label = label, entries = currentMealEntries.toList()))
            }
            currentMealLabel = null
            currentMealEntries = mutableListOf()
        }

        /**
         * Flush the current date section into sections list.
         * Called when we see a new date heading or reach end of input.
         */
        fun flushCurrentSection() {
            val date = currentDate ?: return
            flushCurrentMeal()
            sections.add(
                OrgDateSection(
                    date = date,
                    meals = currentMeals.toList(),
                    exercises = currentExercises.toList()
                )
            )
            currentDate = null
            currentMeals = mutableListOf()
            currentExercises = mutableListOf()
        }

        for (line in content.lines()) {
            when {
                // Level-1 date heading: * <2026-04-09 Thu>
                OrgSchema.DATE_HEADING_REGEX.matches(line) -> {
                    flushCurrentSection()
                    val match = OrgSchema.DATE_HEADING_REGEX.find(line)!!
                    currentDate = LocalDate.parse(match.groupValues[1], OrgSchema.DATE_PARSE_FORMATTER)
                }

                // Level-2 heading: ** SomeLabel
                OrgSchema.MEAL_HEADING_REGEX.matches(line) -> {
                    when (mode) {
                        ParseMode.FOOD -> {
                            // Start a new meal group
                            flushCurrentMeal()
                            val match = OrgSchema.MEAL_HEADING_REGEX.find(line)!!
                            currentMealLabel = match.groupValues[1]
                        }
                        ParseMode.WORKOUT -> {
                            // "** Workout" or similar — just a label, no meal tracking needed
                            // We don't create meal groups in workout mode; skip
                        }
                    }
                }

                // Food entry line: - Name  |P: Xg  C: Xg  F: Xg  Cal: X|
                OrgSchema.FOOD_ENTRY_REGEX.matches(line) && mode == ParseMode.FOOD -> {
                    val match = OrgSchema.FOOD_ENTRY_REGEX.find(line)!!
                    val entry = OrgFoodEntry(
                        name = match.groupValues[1],
                        protein = match.groupValues[2].toInt(),
                        carbs = match.groupValues[3].toInt(),
                        fat = match.groupValues[4].toInt(),
                        calories = match.groupValues[5].toInt()
                    )
                    currentMealEntries.add(entry)
                }

                // Exercise entry line: - Name  |3x5  80kg|
                OrgSchema.EXERCISE_ENTRY_REGEX.matches(line) && mode == ParseMode.WORKOUT -> {
                    val match = OrgSchema.EXERCISE_ENTRY_REGEX.find(line)!!
                    val entry = OrgExerciseEntry(
                        name = match.groupValues[1],
                        sets = match.groupValues[2].toInt(),
                        reps = match.groupValues[3].toInt(),
                        weight = match.groupValues[4].toDouble(),
                        unit = match.groupValues[5]
                    )
                    currentExercises.add(entry)
                }

                // Blank line or unrecognized line — skip gracefully
                else -> { /* no-op */ }
            }
        }

        // Flush the last section (no trailing date heading triggers it)
        flushCurrentSection()

        return OrgFile(sections = sections)
    }
}
