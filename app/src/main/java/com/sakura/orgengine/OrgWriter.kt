package com.sakura.orgengine

import com.sakura.data.food.FoodLibraryItem
import com.sakura.data.food.MealTemplate

/**
 * Serializes OrgFile and OrgDateSection models into valid org-mode text.
 *
 * Format contract:
 *   - Date sections separated by a single blank line
 *   - Meal groups within a date section separated by a single blank line
 *   - No trailing whitespace on any line
 *   - File ends with a single newline
 *   - appendSection prepends new content (newest date first)
 *   - NO blank line between a heading and its :PROPERTIES: drawer (org-mode
 *     requires the drawer to appear immediately after the heading line)
 */
object OrgWriter {

    /**
     * Serialize a complete OrgFile to org-mode text.
     * Sections are written in order, separated by blank lines.
     * File ends with a single newline.
     */
    fun write(orgFile: OrgFile): String {
        if (orgFile.sections.isEmpty()) return ""
        return orgFile.sections.joinToString(separator = "\n\n") { writeSection(it) } + "\n"
    }

    /**
     * Serialize a single date section to org-mode text.
     * Food sections: date heading -> meal subheadings with property-drawer entries (blank line between meals).
     * Workout sections: date heading -> "** Workout" subheading -> exercise property-drawer entries.
     */
    fun writeSection(section: OrgDateSection): String {
        val sb = StringBuilder()
        sb.append(OrgSchema.formatDateHeading(section.date))

        if (section.meals.isNotEmpty()) {
            // Food section: one subheading per meal group, separated by blank lines
            section.meals.forEachIndexed { index, mealGroup ->
                sb.append("\n")
                sb.append(OrgSchema.formatMealHeading(mealGroup.label))
                mealGroup.entries.forEach { entry ->
                    sb.append("\n")
                    sb.append(OrgSchema.formatFoodEntry(entry))
                }
                if (index < section.meals.size - 1) {
                    sb.append("\n") // blank line between meal groups (another \n added at start of next iteration)
                }
            }
        }

        if (section.exercises.isNotEmpty()) {
            // Workout section: single "** Workout" subheading, all exercises listed under it
            sb.append("\n")
            sb.append("** Workout")
            section.exercises.forEach { exercise ->
                sb.append("\n")
                sb.append(OrgSchema.formatExerciseEntry(exercise))
            }
        }

        return sb.toString()
    }

    /**
     * Prepend a new date section to existing file content.
     * The new section appears before the existing content (newest date first).
     * A blank line separates the new section from the existing content.
     */
    fun appendSection(existingContent: String, section: OrgDateSection): String {
        val newSectionText = writeSection(section)
        return if (existingContent.isEmpty()) {
            newSectionText + "\n"
        } else {
            newSectionText + "\n\n" + existingContent
        }
    }

    /**
     * Serialize a complete OrgLibraryFile to org-mode text.
     * Writes the "* Food Library" heading followed by each item as a level-2 heading.
     * File ends with a single newline.
     */
    fun writeLibrary(library: OrgLibraryFile): String {
        val sb = StringBuilder()
        sb.append(OrgSchema.LIBRARY_HEADING)
        library.items.forEach { item ->
            val libItem = FoodLibraryItem(
                id = item.id,
                name = item.name,
                protein = item.protein,
                carbs = item.carbs,
                fat = item.fat,
                calories = item.calories,
                servingSize = item.servingSize,
                servingUnit = item.servingUnit
            )
            sb.append("\n")
            sb.append(OrgSchema.formatLibraryEntry(libItem))
        }
        if (library.items.isNotEmpty()) sb.append("\n")
        return sb.toString()
    }

    /**
     * Serialize a complete OrgTemplateFile to org-mode text.
     * Writes the "* Meal Templates" heading followed by each template as level-2,
     * with template items as level-3. File ends with a single newline.
     */
    fun writeTemplates(templates: OrgTemplateFile): String {
        val sb = StringBuilder()
        sb.append(OrgSchema.TEMPLATES_HEADING)
        templates.templates.forEach { template ->
            val domainTemplate = MealTemplate(
                id = template.id,
                name = template.name,
                entries = template.items.map { item ->
                    FoodLibraryItem(
                        id = item.id,
                        name = item.name,
                        protein = item.protein,
                        carbs = item.carbs,
                        fat = item.fat,
                        calories = item.calories,
                        servingSize = item.servingSize,
                        servingUnit = item.servingUnit
                    )
                }
            )
            sb.append("\n")
            sb.append(OrgSchema.formatTemplateHeading(domainTemplate))
            domainTemplate.entries.forEach { item ->
                sb.append("\n")
                sb.append(OrgSchema.formatTemplateItem(item))
            }
        }
        if (templates.templates.isNotEmpty()) sb.append("\n")
        return sb.toString()
    }
}
