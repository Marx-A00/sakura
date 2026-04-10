package com.sakura.orgengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OrgParserTest {

    private val april9 = LocalDate.of(2026, 4, 9)
    private val april10 = LocalDate.of(2026, 4, 10)

    // -------------------------------------------------------------------------
    // Test 1: Parse single date, single meal, single entry
    // -------------------------------------------------------------------------

    @Test
    fun parseFoodFile_singleDate_singleMeal() {
        val content = """
            |* <2026-04-09 Thu>
            |** Breakfast
            |*** Chicken and rice
            |:PROPERTIES:
            |:protein: 42
            |:carbs: 55
            |:fat: 8
            |:calories: 460
            |:END:
        """.trimMargin()

        val orgFile = OrgParser.parse(content, OrgParser.ParseMode.FOOD)

        assertEquals(1, orgFile.sections.size)
        val section = orgFile.sections[0]
        assertEquals(april9, section.date)
        assertEquals(1, section.meals.size)
        val meal = section.meals[0]
        assertEquals("Breakfast", meal.label)
        assertEquals(1, meal.entries.size)
        val entry = meal.entries[0]
        assertEquals("Chicken and rice", entry.name)
        assertEquals(42, entry.protein)
        assertEquals(55, entry.carbs)
        assertEquals(8, entry.fat)
        assertEquals(460, entry.calories)
    }

    // -------------------------------------------------------------------------
    // Test 2: Parse multiple dates, multiple meals per date
    // -------------------------------------------------------------------------

    @Test
    fun parseFoodFile_multipleDates_multipleMeals() {
        val content = """
            |* <2026-04-10 Fri>
            |** Breakfast
            |*** Oats
            |:PROPERTIES:
            |:protein: 10
            |:carbs: 40
            |:fat: 5
            |:calories: 245
            |:END:
            |** Lunch
            |*** Rice and chicken
            |:PROPERTIES:
            |:protein: 45
            |:carbs: 60
            |:fat: 6
            |:calories: 470
            |:END:
            |
            |* <2026-04-09 Thu>
            |** Breakfast
            |*** Eggs
            |:PROPERTIES:
            |:protein: 20
            |:carbs: 2
            |:fat: 15
            |:calories: 215
            |:END:
        """.trimMargin()

        val orgFile = OrgParser.parse(content, OrgParser.ParseMode.FOOD)

        assertEquals(2, orgFile.sections.size)

        val section1 = orgFile.sections[0]
        assertEquals(april10, section1.date)
        assertEquals(2, section1.meals.size)
        assertEquals("Breakfast", section1.meals[0].label)
        assertEquals(1, section1.meals[0].entries.size)
        assertEquals("Lunch", section1.meals[1].label)
        assertEquals(1, section1.meals[1].entries.size)

        val section2 = orgFile.sections[1]
        assertEquals(april9, section2.date)
        assertEquals(1, section2.meals.size)
        assertEquals("Breakfast", section2.meals[0].label)
        assertEquals(1, section2.meals[0].entries.size)
    }

    // -------------------------------------------------------------------------
    // Test 3: Parse workout file, single date with multiple exercises
    // -------------------------------------------------------------------------

    @Test
    fun parseWorkoutFile_singleDate() {
        val content = """
            |* <2026-04-09 Thu>
            |** Workout
            |*** Squat
            |:PROPERTIES:
            |:sets: 3
            |:reps: 5
            |:weight: 100
            |:unit: kg
            |:END:
            |*** Bench Press
            |:PROPERTIES:
            |:sets: 3
            |:reps: 5
            |:weight: 80
            |:unit: kg
            |:END:
            |*** Deadlift
            |:PROPERTIES:
            |:sets: 1
            |:reps: 5
            |:weight: 140
            |:unit: kg
            |:END:
        """.trimMargin()

        val orgFile = OrgParser.parse(content, OrgParser.ParseMode.WORKOUT)

        assertEquals(1, orgFile.sections.size)
        val section = orgFile.sections[0]
        assertEquals(april9, section.date)
        assertEquals(3, section.exercises.size)

        val squat = section.exercises[0]
        assertEquals("Squat", squat.name)
        assertEquals(3, squat.sets)
        assertEquals(5, squat.reps)
        assertEquals(100.0, squat.weight, 0.001)
        assertEquals("kg", squat.unit)

        val bench = section.exercises[1]
        assertEquals("Bench Press", bench.name)
        assertEquals(3, bench.sets)
        assertEquals(5, bench.reps)
        assertEquals(80.0, bench.weight, 0.001)

        val deadlift = section.exercises[2]
        assertEquals("Deadlift", deadlift.name)
        assertEquals(1, deadlift.sets)
        assertEquals(5, deadlift.reps)
        assertEquals(140.0, deadlift.weight, 0.001)
    }

    // -------------------------------------------------------------------------
    // Test 4: Decimal weight parses correctly
    // -------------------------------------------------------------------------

    @Test
    fun parseExerciseWeight_decimal() {
        val content = """
            |* <2026-04-09 Thu>
            |** Workout
            |*** Overhead Press
            |:PROPERTIES:
            |:sets: 3
            |:reps: 5
            |:weight: 62.5
            |:unit: kg
            |:END:
        """.trimMargin()

        val orgFile = OrgParser.parse(content, OrgParser.ParseMode.WORKOUT)

        assertEquals(1, orgFile.sections.size)
        val exercise = orgFile.sections[0].exercises[0]
        assertEquals(62.5, exercise.weight, 0.001)
    }

    // -------------------------------------------------------------------------
    // Test 5: Round-trip food file (write then parse == original)
    // -------------------------------------------------------------------------

    @Test
    fun roundTrip_foodFile() {
        val original = OrgFile(
            sections = listOf(
                OrgDateSection(
                    date = april9,
                    meals = listOf(
                        OrgMealGroup(
                            label = "Breakfast",
                            entries = listOf(OrgFoodEntry("Oats", 10, 40, 5, 245))
                        ),
                        OrgMealGroup(
                            label = "Lunch",
                            entries = listOf(
                                OrgFoodEntry("Chicken and rice", 42, 55, 8, 460),
                                OrgFoodEntry("Apple", 0, 25, 0, 95)
                            )
                        )
                    ),
                    exercises = emptyList()
                )
            )
        )

        val written = OrgWriter.write(original)
        val parsed = OrgParser.parse(written, OrgParser.ParseMode.FOOD)

        assertEquals(original.sections.size, parsed.sections.size)
        val origSection = original.sections[0]
        val parsedSection = parsed.sections[0]

        assertEquals(origSection.date, parsedSection.date)
        assertEquals(origSection.meals.size, parsedSection.meals.size)

        origSection.meals.forEachIndexed { i, origMeal ->
            val parsedMeal = parsedSection.meals[i]
            assertEquals(origMeal.label, parsedMeal.label)
            assertEquals(origMeal.entries.size, parsedMeal.entries.size)
            origMeal.entries.forEachIndexed { j, origEntry ->
                val parsedEntry = parsedMeal.entries[j]
                assertEquals(origEntry.name, parsedEntry.name)
                assertEquals(origEntry.protein, parsedEntry.protein)
                assertEquals(origEntry.carbs, parsedEntry.carbs)
                assertEquals(origEntry.fat, parsedEntry.fat)
                assertEquals(origEntry.calories, parsedEntry.calories)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Test 6: Round-trip workout file (write then parse == original)
    // -------------------------------------------------------------------------

    @Test
    fun roundTrip_workoutFile() {
        val original = OrgFile(
            sections = listOf(
                OrgDateSection(
                    date = april9,
                    meals = emptyList(),
                    exercises = listOf(
                        OrgExerciseEntry("Squat", 3, 5, 100.0),
                        OrgExerciseEntry("Bench Press", 3, 5, 80.0),
                        OrgExerciseEntry("Romanian Deadlift", 3, 8, 82.5)
                    )
                )
            )
        )

        val written = OrgWriter.write(original)
        val parsed = OrgParser.parse(written, OrgParser.ParseMode.WORKOUT)

        assertEquals(original.sections.size, parsed.sections.size)
        val origSection = original.sections[0]
        val parsedSection = parsed.sections[0]

        assertEquals(origSection.date, parsedSection.date)
        assertEquals(origSection.exercises.size, parsedSection.exercises.size)

        origSection.exercises.forEachIndexed { i, origEx ->
            val parsedEx = parsedSection.exercises[i]
            assertEquals(origEx.name, parsedEx.name)
            assertEquals(origEx.sets, parsedEx.sets)
            assertEquals(origEx.reps, parsedEx.reps)
            assertEquals(origEx.weight, parsedEx.weight, 0.001)
            assertEquals(origEx.unit, parsedEx.unit)
        }
    }

    // -------------------------------------------------------------------------
    // Test 7: Empty file returns empty OrgFile
    // -------------------------------------------------------------------------

    @Test
    fun parseEmptyFile_returnsEmptyOrgFile() {
        val orgFile = OrgParser.parse("", OrgParser.ParseMode.FOOD)
        assertEquals(0, orgFile.sections.size)
    }

    // -------------------------------------------------------------------------
    // Test 8: Malformed line is skipped gracefully (no crash)
    // -------------------------------------------------------------------------

    @Test
    fun parseMalformedLine_skipsGracefully() {
        val content = """
            |* <2026-04-09 Thu>
            |** Breakfast
            |THIS IS NOT A VALID ENTRY LINE
            |*** Oats
            |:PROPERTIES:
            |:protein: 10
            |:carbs: 40
            |:fat: 5
            |:calories: 245
            |:END:
            |ANOTHER BAD LINE
        """.trimMargin()

        // Should not throw, and should parse the valid entry
        val orgFile = OrgParser.parse(content, OrgParser.ParseMode.FOOD)

        assertEquals(1, orgFile.sections.size)
        assertEquals(1, orgFile.sections[0].meals[0].entries.size)
        assertEquals("Oats", orgFile.sections[0].meals[0].entries[0].name)
    }
}
