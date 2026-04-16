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
 *              with level-4 "Set N" headings and property drawers (Phase 3 format).
 *              Backward compat: old flat format (sets/reps/weight/unit on exercise drawer)
 *              is parsed into a single-set OrgExerciseLog.
 *
 * Property drawer parsing:
 *   *** Item name          <- ITEM_HEADING_REGEX captures item name
 *   :PROPERTIES:           <- enter drawer state
 *   :key: value            <- accumulate key/value pairs via PROPERTY_REGEX
 *   :END:                  <- exit drawer state; build and emit entry
 *
 * WORKOUT mode level structure:
 *   ** Workout             <- session heading with metadata drawer
 *   *** Exercise name      <- exercise heading with id/type drawer (level 3)
 *   **** Set 1             <- set heading (level 4)
 *   :PROPERTIES:           <- set drawer with reps/weight/unit/etc
 *   :END:
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

        // Legacy workout accumulators (backward compat)
        var currentExercises = mutableListOf<OrgExerciseEntry>()

        // Phase 3 workout accumulators
        var currentExerciseLogs = mutableListOf<OrgExerciseLog>()
        var currentExerciseLogName: String? = null
        var currentExerciseLogId: Long = 0L
        var currentExerciseLogType: String = "barbell"
        var currentExerciseLogCategory: String? = null
        var currentExerciseLogSets = mutableListOf<OrgSetEntry>()

        // Workout session metadata (from ** Workout property drawer)
        var currentSplitDay: String? = null
        var currentVolume: Int? = null
        var currentDurationMin: Int? = null
        var currentComplete: Boolean = false

        // Set-level tracking
        var currentSetNumber: Int? = null

        // Drawer level tracking: which level is the current drawer for
        // "workout" = ** Workout drawer, "exercise" = *** Exercise drawer, "set" = **** Set drawer
        var currentDrawerContext: String = "none"

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
         * Flush the current exercise log (Phase 3) into exerciseLogs.
         * Called when we see a new level-3 heading or a new date heading.
         */
        fun flushCurrentExerciseLog() {
            val name = currentExerciseLogName ?: return
            val log = OrgExerciseLog(
                name = name,
                id = currentExerciseLogId,
                exerciseType = currentExerciseLogType,
                sets = currentExerciseLogSets.toList(),
                category = currentExerciseLogCategory
            )
            currentExerciseLogs.add(log)
            currentExerciseLogName = null
            currentExerciseLogId = 0L
            currentExerciseLogType = "barbell"
            currentExerciseLogCategory = null
            currentExerciseLogSets = mutableListOf()
            currentSetNumber = null
        }

        /**
         * Flush the current date section into sections list.
         * Called when we see a new date heading or reach end of input.
         */
        fun flushCurrentSection() {
            val date = currentDate ?: return
            flushCurrentMeal()
            if (mode == ParseMode.WORKOUT) {
                flushCurrentExerciseLog()
            }
            sections.add(
                OrgDateSection(
                    date = date,
                    meals = currentMeals.toList(),
                    exercises = currentExercises.toList(),
                    exerciseLogs = currentExerciseLogs.toList(),
                    splitDay = currentSplitDay,
                    volume = currentVolume,
                    durationMin = currentDurationMin,
                    complete = currentComplete
                )
            )
            currentDate = null
            currentMeals = mutableListOf()
            currentExercises = mutableListOf()
            currentExerciseLogs = mutableListOf()
            currentSplitDay = null
            currentVolume = null
            currentDurationMin = null
            currentComplete = false
        }

        for (line in content.lines()) {
            when {
                // Level-1 date heading: * <2026-04-09 Thu>
                OrgSchema.DATE_HEADING_REGEX.matches(line) -> {
                    flushCurrentSection()
                    val match = OrgSchema.DATE_HEADING_REGEX.find(line)!!
                    currentDate = LocalDate.parse(match.groupValues[1], OrgSchema.DATE_PARSE_FORMATTER)
                    currentDrawerContext = "none"
                }

                // Level-4 set heading: **** Set N (WORKOUT mode only, check before level-3)
                mode == ParseMode.WORKOUT && OrgSchema.SET_HEADING_REGEX.matches(line) -> {
                    val match = OrgSchema.SET_HEADING_REGEX.find(line)!!
                    currentSetNumber = match.groupValues[1].toIntOrNull() ?: 1
                    currentDrawerContext = "set"
                    drawerProperties = mutableMapOf()
                }

                // Level-3 item heading: *** Item name
                OrgSchema.ITEM_HEADING_REGEX.matches(line) -> {
                    val match = OrgSchema.ITEM_HEADING_REGEX.find(line)!!
                    val name = match.groupValues[1]
                    when (mode) {
                        ParseMode.FOOD -> {
                            currentItemName = name
                            drawerProperties = mutableMapOf()
                        }
                        ParseMode.WORKOUT -> {
                            // Flush previous exercise log before starting a new one
                            flushCurrentExerciseLog()
                            currentExerciseLogName = name
                            currentDrawerContext = "exercise"
                            currentItemName = name
                            drawerProperties = mutableMapOf()
                        }
                    }
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
                            // "** Workout" — session-level heading; prepare to read metadata drawer
                            currentDrawerContext = "workout"
                            drawerProperties = mutableMapOf()
                        }
                    }
                }

                // Property drawer open
                line.trim() == OrgSchema.PROPERTIES_START -> {
                    inPropertyDrawer = true
                    drawerProperties = mutableMapOf()
                }

                // Property drawer close — build and emit entry from accumulated state
                line.trim() == OrgSchema.PROPERTIES_END && inPropertyDrawer -> {
                    inPropertyDrawer = false
                    when (mode) {
                        ParseMode.FOOD -> {
                            val itemName = currentItemName
                            if (itemName != null) {
                                val entry = OrgFoodEntry(
                                    name = itemName,
                                    protein = drawerProperties[OrgSchema.PROP_PROTEIN]?.toIntOrNull() ?: 0,
                                    carbs = drawerProperties[OrgSchema.PROP_CARBS]?.toIntOrNull() ?: 0,
                                    fat = drawerProperties[OrgSchema.PROP_FAT]?.toIntOrNull() ?: 0,
                                    calories = drawerProperties[OrgSchema.PROP_CALORIES]?.toIntOrNull() ?: 0,
                                    id = drawerProperties[OrgSchema.PROP_ID]?.toLongOrNull() ?: 0L,
                                    servingSize = drawerProperties[OrgSchema.PROP_SERVING_SIZE],
                                    servingUnit = drawerProperties[OrgSchema.PROP_SERVING_UNIT],
                                    notes = drawerProperties[OrgSchema.PROP_NOTES]
                                )
                                currentMealEntries.add(entry)
                            }
                            currentItemName = null
                            drawerProperties = mutableMapOf()
                        }
                        ParseMode.WORKOUT -> {
                            when (currentDrawerContext) {
                                "workout" -> {
                                    // Session-level metadata drawer
                                    currentSplitDay = drawerProperties[OrgSchema.PROP_SPLIT_DAY]
                                    currentVolume = drawerProperties[OrgSchema.PROP_VOLUME]?.toIntOrNull()
                                    currentDurationMin = drawerProperties[OrgSchema.PROP_DURATION_MIN]?.toIntOrNull()
                                    currentComplete = drawerProperties[OrgSchema.PROP_COMPLETE]?.toBooleanStrictOrNull() ?: false
                                    drawerProperties = mutableMapOf()
                                }
                                "exercise" -> {
                                    // Exercise-level drawer: read id, exercise_type, and category
                                    // Also check for old flat format (sets/reps/weight/unit)
                                    currentExerciseLogId = drawerProperties[OrgSchema.PROP_ID]?.toLongOrNull() ?: 0L
                                    currentExerciseLogType = drawerProperties[OrgSchema.PROP_EXERCISE_TYPE] ?: "barbell"
                                    currentExerciseLogCategory = drawerProperties[OrgSchema.PROP_CATEGORY]

                                    // Backward compat: if old flat format properties found and no set headings follow,
                                    // synthesize a single OrgSetEntry. We detect this when :sets: is present.
                                    val oldSets = drawerProperties[OrgSchema.PROP_SETS]?.toIntOrNull()
                                    if (oldSets != null) {
                                        // Old format — synthesize single set entry
                                        val syntheticSet = OrgSetEntry(
                                            setNumber = 1,
                                            reps = drawerProperties[OrgSchema.PROP_REPS]?.toIntOrNull() ?: 0,
                                            weight = drawerProperties[OrgSchema.PROP_WEIGHT]?.toDoubleOrNull() ?: 0.0,
                                            unit = drawerProperties[OrgSchema.PROP_UNIT] ?: "kg",
                                            holdSecs = 0,
                                            rpe = null,
                                            isPr = false
                                        )
                                        currentExerciseLogSets.add(syntheticSet)
                                        // Also add to legacy exercises list for backward compat
                                        val legacyEntry = OrgExerciseEntry(
                                            name = currentExerciseLogName ?: "",
                                            sets = oldSets,
                                            reps = drawerProperties[OrgSchema.PROP_REPS]?.toIntOrNull() ?: 0,
                                            weight = drawerProperties[OrgSchema.PROP_WEIGHT]?.toDoubleOrNull() ?: 0.0,
                                            unit = drawerProperties[OrgSchema.PROP_UNIT] ?: "kg"
                                        )
                                        currentExercises.add(legacyEntry)
                                    }
                                    drawerProperties = mutableMapOf()
                                }
                                "set" -> {
                                    // Set-level drawer: build OrgSetEntry including cardio fields
                                    val setNum = currentSetNumber ?: 1
                                    val setEntry = OrgSetEntry(
                                        setNumber = setNum,
                                        reps = drawerProperties[OrgSchema.PROP_REPS]?.toIntOrNull() ?: 0,
                                        weight = drawerProperties[OrgSchema.PROP_WEIGHT]?.toDoubleOrNull() ?: 0.0,
                                        unit = drawerProperties[OrgSchema.PROP_UNIT] ?: "kg",
                                        holdSecs = drawerProperties[OrgSchema.PROP_HOLD_SECS]?.toIntOrNull() ?: 0,
                                        rpe = drawerProperties[OrgSchema.PROP_RPE]?.toIntOrNull(),
                                        isPr = drawerProperties[OrgSchema.PROP_IS_PR]?.toBooleanStrictOrNull() ?: false,
                                        durationMin = drawerProperties[OrgSchema.PROP_DURATION_MIN]?.toIntOrNull(),
                                        distanceKm = drawerProperties[OrgSchema.PROP_DISTANCE_KM]?.toDoubleOrNull()
                                    )
                                    currentExerciseLogSets.add(setEntry)
                                    currentSetNumber = null
                                    drawerProperties = mutableMapOf()
                                }
                                else -> {
                                    drawerProperties = mutableMapOf()
                                }
                            }
                        }
                    }
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

    /**
     * Parse food-library.org content into an OrgLibraryFile.
     *
     * Expected format:
     *   * Food Library
     *   ** Chicken Breast
     *   :PROPERTIES:
     *   :id: a1b2c3d4
     *   :protein: 31
     *   ...
     *   :END:
     *
     * The level-1 "* Food Library" heading is skipped; items are level-2 headings.
     * If the file is empty or malformed, returns an empty library.
     */
    fun parseLibrary(content: String): OrgLibraryFile {
        if (content.isBlank()) return OrgLibraryFile(items = emptyList())

        val items = mutableListOf<OrgLibraryEntry>()
        var currentItemName: String? = null
        var inPropertyDrawer = false
        var drawerProperties = mutableMapOf<String, String>()

        for (line in content.lines()) {
            when {
                // Level-1 heading (e.g., "* Food Library") — skip
                line.startsWith("* ") && !line.startsWith("** ") -> { /* skip */ }

                // Level-2 item heading: ** Item name
                OrgSchema.LIBRARY_ITEM_HEADING_REGEX.matches(line) -> {
                    val match = OrgSchema.LIBRARY_ITEM_HEADING_REGEX.find(line)!!
                    currentItemName = match.groupValues[1]
                    drawerProperties = mutableMapOf()
                }

                // Property drawer open
                line.trim() == OrgSchema.PROPERTIES_START -> {
                    inPropertyDrawer = true
                }

                // Property drawer close — emit library item
                line.trim() == OrgSchema.PROPERTIES_END && inPropertyDrawer -> {
                    inPropertyDrawer = false
                    val itemName = currentItemName
                    if (itemName != null) {
                        val entry = OrgLibraryEntry(
                            id = drawerProperties[OrgSchema.PROP_ID] ?: "",
                            name = itemName,
                            protein = drawerProperties[OrgSchema.PROP_PROTEIN]?.toIntOrNull() ?: 0,
                            carbs = drawerProperties[OrgSchema.PROP_CARBS]?.toIntOrNull() ?: 0,
                            fat = drawerProperties[OrgSchema.PROP_FAT]?.toIntOrNull() ?: 0,
                            calories = drawerProperties[OrgSchema.PROP_CALORIES]?.toIntOrNull() ?: 0,
                            servingSize = drawerProperties[OrgSchema.PROP_SERVING_SIZE],
                            servingUnit = drawerProperties[OrgSchema.PROP_SERVING_UNIT]
                        )
                        items.add(entry)
                    }
                    currentItemName = null
                    drawerProperties = mutableMapOf()
                }

                // Property key-value inside a drawer
                inPropertyDrawer && OrgSchema.PROPERTY_REGEX.matches(line) -> {
                    val match = OrgSchema.PROPERTY_REGEX.find(line)!!
                    drawerProperties[match.groupValues[1]] = match.groupValues[2]
                }

                else -> { /* skip */ }
            }
        }

        return OrgLibraryFile(items = items)
    }

    /**
     * Parse meal-templates.org content into an OrgTemplateFile.
     *
     * Expected format:
     *   * Meal Templates
     *   ** Weekday Breakfast
     *   :PROPERTIES:
     *   :id: e5f6g7h8
     *   :END:
     *   *** Oatmeal
     *   :PROPERTIES:
     *   :protein: 5
     *   ...
     *   :END:
     *
     * Level-2 headings are templates; level-3 headings under them are template items.
     * The template's :id: property drawer comes immediately after the level-2 heading.
     * If the file is empty or malformed, returns an empty template file.
     */
    fun parseTemplates(content: String): OrgTemplateFile {
        if (content.isBlank()) return OrgTemplateFile(templates = emptyList())

        val templates = mutableListOf<OrgMealTemplate>()

        // Current template accumulator
        var currentTemplateId: String? = null
        var currentTemplateName: String? = null
        var currentTemplateItems = mutableListOf<OrgLibraryEntry>()

        // Current item accumulator
        var currentItemName: String? = null
        var inPropertyDrawer = false
        var drawerProperties = mutableMapOf<String, String>()

        // Track whether the current drawer belongs to a template heading or an item
        // true = we're inside the template-level drawer (id only), false = item drawer
        var inTemplateDrawer = false

        fun flushCurrentTemplate() {
            val id = currentTemplateId
            val name = currentTemplateName
            if (id != null && name != null) {
                templates.add(OrgMealTemplate(id = id, name = name, items = currentTemplateItems.toList()))
            }
            currentTemplateId = null
            currentTemplateName = null
            currentTemplateItems = mutableListOf()
        }

        for (line in content.lines()) {
            when {
                // Level-1 heading (e.g., "* Meal Templates") — skip
                line.startsWith("* ") && !line.startsWith("** ") -> { /* skip */ }

                // Level-2 template heading: ** Template name
                OrgSchema.TEMPLATE_HEADING_REGEX.matches(line) -> {
                    flushCurrentTemplate()
                    val match = OrgSchema.TEMPLATE_HEADING_REGEX.find(line)!!
                    currentTemplateName = match.groupValues[1]
                    currentItemName = null
                    drawerProperties = mutableMapOf()
                    inTemplateDrawer = false
                }

                // Level-3 item heading: *** Item name
                OrgSchema.TEMPLATE_ITEM_HEADING_REGEX.matches(line) -> {
                    val match = OrgSchema.TEMPLATE_ITEM_HEADING_REGEX.find(line)!!
                    currentItemName = match.groupValues[1]
                    drawerProperties = mutableMapOf()
                    inTemplateDrawer = false
                }

                // Property drawer open
                line.trim() == OrgSchema.PROPERTIES_START -> {
                    inPropertyDrawer = true
                    // If we just set a template name and have no item yet, this is the template drawer
                    inTemplateDrawer = (currentItemName == null && currentTemplateName != null && currentTemplateId == null)
                }

                // Property drawer close
                line.trim() == OrgSchema.PROPERTIES_END && inPropertyDrawer -> {
                    inPropertyDrawer = false
                    if (inTemplateDrawer) {
                        // Template-level drawer: extract id
                        currentTemplateId = drawerProperties[OrgSchema.PROP_ID]
                        inTemplateDrawer = false
                    } else {
                        // Item-level drawer: emit library entry
                        val itemName = currentItemName
                        if (itemName != null) {
                            val entry = OrgLibraryEntry(
                                id = "",   // template items don't have their own UUID
                                name = itemName,
                                protein = drawerProperties[OrgSchema.PROP_PROTEIN]?.toIntOrNull() ?: 0,
                                carbs = drawerProperties[OrgSchema.PROP_CARBS]?.toIntOrNull() ?: 0,
                                fat = drawerProperties[OrgSchema.PROP_FAT]?.toIntOrNull() ?: 0,
                                calories = drawerProperties[OrgSchema.PROP_CALORIES]?.toIntOrNull() ?: 0,
                                servingSize = drawerProperties[OrgSchema.PROP_SERVING_SIZE],
                                servingUnit = drawerProperties[OrgSchema.PROP_SERVING_UNIT]
                            )
                            currentTemplateItems.add(entry)
                        }
                        currentItemName = null
                    }
                    drawerProperties = mutableMapOf()
                }

                // Property key-value inside a drawer
                inPropertyDrawer && OrgSchema.PROPERTY_REGEX.matches(line) -> {
                    val match = OrgSchema.PROPERTY_REGEX.find(line)!!
                    drawerProperties[match.groupValues[1]] = match.groupValues[2]
                }

                else -> { /* skip */ }
            }
        }

        // Flush the last template
        flushCurrentTemplate()

        return OrgTemplateFile(templates = templates)
    }

    // -------------------------------------------------------------------------
    // Workout templates (workout-templates.org)
    // -------------------------------------------------------------------------

    /**
     * Parse workout-templates.org content into an [OrgWorkoutTemplateFile].
     * Structure mirrors [parseTemplates] — level-2 templates with :id: drawer,
     * level-3 exercise items with :category: and :muscle_groups: drawers.
     */
    fun parseWorkoutTemplates(content: String): OrgWorkoutTemplateFile {
        if (content.isBlank()) return OrgWorkoutTemplateFile(templates = emptyList())

        val templates = mutableListOf<OrgWorkoutTemplate>()

        var currentTemplateId: String? = null
        var currentTemplateName: String? = null
        var currentExercises = mutableListOf<OrgWorkoutTemplateExercise>()

        var currentItemName: String? = null
        var inPropertyDrawer = false
        var drawerProperties = mutableMapOf<String, String>()
        var inTemplateDrawer = false

        fun flushCurrentTemplate() {
            val id = currentTemplateId
            val name = currentTemplateName
            if (id != null && name != null) {
                templates.add(OrgWorkoutTemplate(id = id, name = name, exercises = currentExercises.toList()))
            }
            currentTemplateId = null
            currentTemplateName = null
            currentExercises = mutableListOf()
        }

        for (line in content.lines()) {
            when {
                // Level-1 heading — skip
                line.startsWith("* ") && !line.startsWith("** ") -> { /* skip */ }

                // Level-2 template heading
                OrgSchema.TEMPLATE_HEADING_REGEX.matches(line) -> {
                    flushCurrentTemplate()
                    val match = OrgSchema.TEMPLATE_HEADING_REGEX.find(line)!!
                    currentTemplateName = match.groupValues[1]
                    currentItemName = null
                    drawerProperties = mutableMapOf()
                    inTemplateDrawer = false
                }

                // Level-3 exercise heading
                OrgSchema.TEMPLATE_ITEM_HEADING_REGEX.matches(line) -> {
                    val match = OrgSchema.TEMPLATE_ITEM_HEADING_REGEX.find(line)!!
                    currentItemName = match.groupValues[1]
                    drawerProperties = mutableMapOf()
                    inTemplateDrawer = false
                }

                // Property drawer open
                line.trim() == OrgSchema.PROPERTIES_START -> {
                    inPropertyDrawer = true
                    inTemplateDrawer = (currentItemName == null && currentTemplateName != null && currentTemplateId == null)
                }

                // Property drawer close
                line.trim() == OrgSchema.PROPERTIES_END && inPropertyDrawer -> {
                    inPropertyDrawer = false
                    if (inTemplateDrawer) {
                        currentTemplateId = drawerProperties[OrgSchema.PROP_ID]
                        inTemplateDrawer = false
                    } else {
                        val itemName = currentItemName
                        if (itemName != null) {
                            val exercise = OrgWorkoutTemplateExercise(
                                name = itemName,
                                categoryLabel = drawerProperties[OrgSchema.PROP_CATEGORY] ?: "weighted",
                                muscleGroups = drawerProperties[OrgSchema.PROP_MUSCLE_GROUPS] ?: "",
                                targetSets = drawerProperties[OrgSchema.PROP_TARGET_SETS]?.toIntOrNull() ?: 0,
                                targetReps = drawerProperties[OrgSchema.PROP_TARGET_REPS]?.toIntOrNull() ?: 0,
                                targetHoldSecs = drawerProperties[OrgSchema.PROP_TARGET_HOLD_SECS]?.toIntOrNull() ?: 0
                            )
                            currentExercises.add(exercise)
                        }
                        currentItemName = null
                    }
                    drawerProperties = mutableMapOf()
                }

                // Property key-value
                inPropertyDrawer && OrgSchema.PROPERTY_REGEX.matches(line) -> {
                    val match = OrgSchema.PROPERTY_REGEX.find(line)!!
                    drawerProperties[match.groupValues[1]] = match.groupValues[2]
                }

                else -> { /* skip */ }
            }
        }

        flushCurrentTemplate()
        return OrgWorkoutTemplateFile(templates = templates)
    }
}
