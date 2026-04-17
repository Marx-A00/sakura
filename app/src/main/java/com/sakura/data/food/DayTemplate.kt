package com.sakura.data.food

import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved full day of eating — multiple meals with their food items.
 * Lets the user bulk-apply a recurring day (e.g., "Standard Weekday").
 *
 * Persisted as JSON via SyncBackend (day-templates.json).
 */
data class DayTemplate(
    val id: String,
    val name: String,
    val meals: List<DayTemplateMeal>
) {
    val totalCalories: Int get() = meals.sumOf { m -> m.items.sumOf { it.calories } }
    val itemCount: Int get() = meals.sumOf { it.items.size }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("meals", JSONArray().apply {
            meals.forEach { meal ->
                put(JSONObject().apply {
                    put("label", meal.label)
                    put("items", JSONArray().apply {
                        meal.items.forEach { item ->
                            put(JSONObject().apply {
                                put("name", item.name)
                                put("protein", item.protein)
                                put("carbs", item.carbs)
                                put("fat", item.fat)
                                put("calories", item.calories)
                                if (item.servingSize != null) put("serving_size", item.servingSize)
                                if (item.servingUnit != null) put("serving_unit", item.servingUnit)
                            })
                        }
                    })
                })
            }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): DayTemplate {
            val mealsArr = json.getJSONArray("meals")
            val meals = (0 until mealsArr.length()).map { i ->
                val mealObj = mealsArr.getJSONObject(i)
                val itemsArr = mealObj.getJSONArray("items")
                DayTemplateMeal(
                    label = mealObj.getString("label"),
                    items = (0 until itemsArr.length()).map { j ->
                        val itemObj = itemsArr.getJSONObject(j)
                        FoodLibraryItem(
                            id = "",  // not persisted in template — regenerated on apply
                            name = itemObj.getString("name"),
                            protein = itemObj.getInt("protein"),
                            carbs = itemObj.getInt("carbs"),
                            fat = itemObj.getInt("fat"),
                            calories = itemObj.getInt("calories"),
                            servingSize = itemObj.optString("serving_size", null),
                            servingUnit = itemObj.optString("serving_unit", null)
                        )
                    }
                )
            }
            return DayTemplate(
                id = json.getString("id"),
                name = json.getString("name"),
                meals = meals
            )
        }

        fun listFromJson(raw: String): List<DayTemplate> {
            if (raw.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun listToJson(templates: List<DayTemplate>): String {
            return JSONArray().apply {
                templates.forEach { put(it.toJson()) }
            }.toString(2)
        }
    }
}

/**
 * A single meal within a day template.
 */
data class DayTemplateMeal(
    val label: String,                  // "Breakfast", "Lunch", "Dinner", "Snacks"
    val items: List<FoodLibraryItem>
)
