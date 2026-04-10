package com.sakura.orgengine

import java.time.LocalDate

/**
 * Deserializes org-mode text into OrgFile models.
 *
 * Uses a sequential state machine that walks lines top-to-bottom.
 * Unrecognized lines are silently skipped (graceful degradation).
 *
 * Parse modes:
 *   FOOD    -- expects meal group headings (level 2) and food item headings (level 3)
 *              with property drawers containing protein/carbs/fat/calories
 *   WORKOUT -- expects a workout heading (level 2) and exercise headings (level 3)
 *              with property drawers containing sets/reps/weight/unit
 *
 * Property drawer parsing:
 *   *** Item name          <- ITEM_HEADING_REGEX captures item name
 *   :PROPERTIES:           <- enter drawer state
 *   :key: value            <- accumulate key/value pairs via PROPERTY_REGEX
 *   :END:                  <- exit drawer state; build and emit entry
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

        // Property drawer state
        var inPropertyDrawer = false
        var currentItemName: String? = null
        var drawerProperties = mutableMapOf<String, String>()

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

                // Level-2 heading: ** SomeLabel (meal group or workout)
                OrgSchema.MEAL_HEADING_REGEX.matches(line) -> {
                    when (mode) {
                        ParseMode.FOOD -> {
                            // Start a new meal group
                            flushCurrentMeal()
                            val match = OrgSchema.MEAL_HEADING_REGEX.find(line)!!
                            currentMealLabel = match.groupValues[1]
                        }
                        ParseMode.WORKOUT -> {
                            // "** Workout" — just a label, no meal tracking needed
                        }
                    }
                }

                // Level-3 item heading: *** Item name
                OrgSchema.ITEM_HEADING_REGEX.matches(line) -> {
                    val match = OrgSchema.ITEM_HEADING_REGEX.find(line)!!
                    currentItemName = match.groupValues[1]
                    drawerProperties = mutableMapOf()
                }

                // Property drawer open
                line.trim() == OrgSchema.PROPERTIES_START -> {
                    inPropertyDrawer = true
                }

                // Property drawer close — build and emit entry from accumulated state
                line.trim() == OrgSchema.PROPERTIES_END && inPropertyDrawer -> {
                    inPropertyDrawer = false
                    val itemName = currentItemName
                    if (itemName != null) {
                        when (mode) {
                            ParseMode.FOOD -> {
                                val entry = OrgFoodEntry(
                                    name = itemName,
                                    protein = drawerProperties["protein"]?.toIntOrNull() ?: 0,
                                    carbs = drawerProperties["carbs"]?.toIntOrNull() ?: 0,
                                    fat = drawerProperties["fat"]?.toIntOrNull() ?: 0,
                                    calories = drawerProperties["calories"]?.toIntOrNull() ?: 0
                                )
                                currentMealEntries.add(entry)
                            }
                            ParseMode.WORKOUT -> {
                                val entry = OrgExerciseEntry(
                                    name = itemName,
                                    sets = drawerProperties["sets"]?.toIntOrNull() ?: 0,
                                    reps = drawerProperties["reps"]?.toIntOrNull() ?: 0,
                                    weight = drawerProperties["weight"]?.toDoubleOrNull() ?: 0.0,
                                    unit = drawerProperties["unit"] ?: "kg"
                                )
                                currentExercises.add(entry)
                            }
                        }
                    }
                    currentItemName = null
                    drawerProperties = mutableMapOf()
                }

                // Property key-value inside a drawer: :key: value
                inPropertyDrawer && OrgSchema.PROPERTY_REGEX.matches(line) -> {
                    val match = OrgSchema.PROPERTY_REGEX.find(line)!!
                    drawerProperties[match.groupValues[1]] = match.groupValues[2]
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
