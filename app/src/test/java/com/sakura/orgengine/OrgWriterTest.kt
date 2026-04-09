package com.sakura.orgengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OrgWriterTest {

    private val april9 = LocalDate.of(2026, 4, 9)
    private val april10 = LocalDate.of(2026, 4, 10)

    // -------------------------------------------------------------------------
    // Test 1: Single meal produces valid org output
    // -------------------------------------------------------------------------

    @Test
    fun writeFoodSection_singleMeal_producesValidOrg() {
        val section = OrgDateSection(
            date = april9,
            meals = listOf(
                OrgMealGroup(
                    label = "Breakfast",
                    entries = listOf(OrgFoodEntry("Chicken and rice", 42, 55, 8, 460))
                )
            ),
            exercises = emptyList()
        )

        val expected = """
            |* <2026-04-09 Thu>
            |** Breakfast
            |- Chicken and rice  |P: 42g  C: 55g  F: 8g  Cal: 460|
        """.trimMargin()

        assertEquals(expected, OrgWriter.writeSection(section).trimEnd())
    }

    // -------------------------------------------------------------------------
    // Test 2: Multiple meals are separated by a blank line
    // -------------------------------------------------------------------------

    @Test
    fun writeFoodSection_multipleMeals_correctStructure() {
        val section = OrgDateSection(
            date = april9,
            meals = listOf(
                OrgMealGroup(
                    label = "Breakfast",
                    entries = listOf(OrgFoodEntry("Oats", 10, 40, 5, 245))
                ),
                OrgMealGroup(
                    label = "Lunch",
                    entries = listOf(OrgFoodEntry("Rice and chicken", 45, 60, 6, 470))
                )
            ),
            exercises = emptyList()
        )

        val result = OrgWriter.writeSection(section)

        // Both meal headings must be present
        assertTrue(result.contains("** Breakfast"))
        assertTrue(result.contains("** Lunch"))

        // There must be a blank line between the two meal groups
        assertTrue("Expected blank line between meal groups", result.contains("\n\n"))
    }

    // -------------------------------------------------------------------------
    // Test 3: Workout section serializes exercise entries
    // -------------------------------------------------------------------------

    @Test
    fun writeWorkoutSection_singleExercise_producesValidOrg() {
        val section = OrgDateSection(
            date = april9,
            meals = emptyList(),
            exercises = listOf(OrgExerciseEntry("Bench Press", 3, 5, 80.0))
        )

        val expected = "* <2026-04-09 Thu>\n** Workout\n- Bench Press  |3x5  80kg|"

        assertEquals(expected, OrgWriter.writeSection(section).trimEnd())
    }

    // -------------------------------------------------------------------------
    // Test 4: Multiple exercises all listed under Workout heading
    // -------------------------------------------------------------------------

    @Test
    fun writeWorkoutSection_multipleExercises_allListed() {
        val section = OrgDateSection(
            date = april9,
            meals = emptyList(),
            exercises = listOf(
                OrgExerciseEntry("Squat", 3, 5, 100.0),
                OrgExerciseEntry("Bench Press", 3, 5, 80.0),
                OrgExerciseEntry("Deadlift", 1, 5, 140.0)
            )
        )

        val result = OrgWriter.writeSection(section)

        assertTrue(result.contains("** Workout"))
        assertTrue(result.contains("- Squat  |3x5  100kg|"))
        assertTrue(result.contains("- Bench Press  |3x5  80kg|"))
        assertTrue(result.contains("- Deadlift  |1x5  140kg|"))
    }

    // -------------------------------------------------------------------------
    // Test 5: Multiple date sections separated by blank line
    // -------------------------------------------------------------------------

    @Test
    fun writeFile_multipleDates_separatedByBlankLine() {
        val orgFile = OrgFile(
            sections = listOf(
                OrgDateSection(
                    date = april10,
                    meals = listOf(
                        OrgMealGroup("Breakfast", listOf(OrgFoodEntry("Eggs", 20, 2, 15, 215)))
                    ),
                    exercises = emptyList()
                ),
                OrgDateSection(
                    date = april9,
                    meals = listOf(
                        OrgMealGroup("Breakfast", listOf(OrgFoodEntry("Oats", 10, 40, 5, 245)))
                    ),
                    exercises = emptyList()
                )
            )
        )

        val result = OrgWriter.write(orgFile)

        // Both date headings must be present
        assertTrue(result.contains("* <2026-04-10 Fri>"))
        assertTrue(result.contains("* <2026-04-09 Thu>"))

        // Sections must be separated by a blank line
        assertTrue("Expected blank line between date sections", result.contains("\n\n"))
    }

    // -------------------------------------------------------------------------
    // Test 6: appendSection prepends new section to existing content
    // -------------------------------------------------------------------------

    @Test
    fun writeFile_appendToExisting_preservesPriorContent() {
        val existingContent = """* <2026-04-09 Thu>
** Breakfast
- Oats  |P: 10g  C: 40g  F: 5g  Cal: 245|"""

        val newSection = OrgDateSection(
            date = april10,
            meals = listOf(
                OrgMealGroup("Breakfast", listOf(OrgFoodEntry("Eggs", 20, 2, 15, 215)))
            ),
            exercises = emptyList()
        )

        val result = OrgWriter.appendSection(existingContent, newSection)

        // New section appears first (newest date first)
        val newIdx = result.indexOf("* <2026-04-10 Fri>")
        val oldIdx = result.indexOf("* <2026-04-09 Thu>")
        assertTrue("New section should appear before existing content", newIdx < oldIdx)

        // Existing content is preserved unchanged
        assertTrue(result.contains(existingContent))

        // Separated by a blank line
        assertTrue("Expected blank line between sections", result.contains("\n\n"))
    }

    // -------------------------------------------------------------------------
    // Test 7: Integer weight renders without decimal point
    // -------------------------------------------------------------------------

    @Test
    fun writeFoodEntry_integerWeight_noDecimalPoint() {
        val section = OrgDateSection(
            date = april9,
            meals = emptyList(),
            exercises = listOf(OrgExerciseEntry("Bench Press", 3, 5, 80.0))
        )

        val result = OrgWriter.writeSection(section)

        assertTrue("Weight 80.0 should render as '80kg', not '80.0kg'", result.contains("80kg"))
        assertTrue("Should not contain '80.0kg'", !result.contains("80.0kg"))
    }
}
