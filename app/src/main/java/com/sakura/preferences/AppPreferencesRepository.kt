package com.sakura.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "sakura_prefs")

/**
 * Persists user preferences using Jetpack DataStore.
 * All reads are exposed as Flows; never blocks the calling coroutine.
 */
class AppPreferencesRepository(private val context: Context) {

    companion object {
        val SYNC_FOLDER_PATH = stringPreferencesKey("sync_folder_path")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

        // Macro targets — daily nutrition goals
        val MACRO_TARGET_CALORIES = intPreferencesKey("macro_target_calories")
        val MACRO_TARGET_PROTEIN = intPreferencesKey("macro_target_protein")
        val MACRO_TARGET_CARBS = intPreferencesKey("macro_target_carbs")
        val MACRO_TARGET_FAT = intPreferencesKey("macro_target_fat")
    }

    /** The user-configured Syncthing folder path, or null if not yet set. */
    val syncFolderPath: Flow<String?> = context.appDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[SYNC_FOLDER_PATH] }

    /** Whether the user has completed onboarding. Defaults to false. */
    val onboardingComplete: Flow<Boolean> = context.appDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[ONBOARDING_COMPLETE] ?: false }

    // -------------------------------------------------------------------------
    // Macro target flows (with sensible defaults)
    // -------------------------------------------------------------------------

    /** Daily calorie target. Default: 2000 kcal. */
    val macroTargetCalories: Flow<Int> = context.appDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[MACRO_TARGET_CALORIES] ?: 2000 }

    /** Daily protein target in grams. Default: 150g. */
    val macroTargetProtein: Flow<Int> = context.appDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[MACRO_TARGET_PROTEIN] ?: 150 }

    /** Daily carbohydrate target in grams. Default: 250g. */
    val macroTargetCarbs: Flow<Int> = context.appDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[MACRO_TARGET_CARBS] ?: 250 }

    /** Daily fat target in grams. Default: 65g. */
    val macroTargetFat: Flow<Int> = context.appDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[MACRO_TARGET_FAT] ?: 65 }

    /** Combined flow of all four macro targets for convenience. */
    val macroTargets: Flow<MacroTargets> = combine(
        macroTargetCalories,
        macroTargetProtein,
        macroTargetCarbs,
        macroTargetFat
    ) { calories, protein, carbs, fat ->
        MacroTargets(calories = calories, protein = protein, carbs = carbs, fat = fat)
    }

    // -------------------------------------------------------------------------
    // Setter functions
    // -------------------------------------------------------------------------

    suspend fun setSyncFolderPath(path: String) {
        context.appDataStore.edit { preferences ->
            preferences[SYNC_FOLDER_PATH] = path
        }
    }

    suspend fun setOnboardingComplete() {
        context.appDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun setMacroTargetCalories(calories: Int) {
        context.appDataStore.edit { preferences ->
            preferences[MACRO_TARGET_CALORIES] = calories
        }
    }

    suspend fun setMacroTargetProtein(protein: Int) {
        context.appDataStore.edit { preferences ->
            preferences[MACRO_TARGET_PROTEIN] = protein
        }
    }

    suspend fun setMacroTargetCarbs(carbs: Int) {
        context.appDataStore.edit { preferences ->
            preferences[MACRO_TARGET_CARBS] = carbs
        }
    }

    suspend fun setMacroTargetFat(fat: Int) {
        context.appDataStore.edit { preferences ->
            preferences[MACRO_TARGET_FAT] = fat
        }
    }

    /** Set all macro targets at once in a single DataStore edit. */
    suspend fun setMacroTargets(targets: MacroTargets) {
        context.appDataStore.edit { preferences ->
            preferences[MACRO_TARGET_CALORIES] = targets.calories
            preferences[MACRO_TARGET_PROTEIN] = targets.protein
            preferences[MACRO_TARGET_CARBS] = targets.carbs
            preferences[MACRO_TARGET_FAT] = targets.fat
        }
    }
}

/**
 * Convenience data class grouping all four daily macro targets.
 */
data class MacroTargets(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)
