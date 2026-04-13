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
 * Storage mode selection: LOCAL = internal storage (no sync), SYNCTHING = external shared folder.
 * Persisted to DataStore as a string (enum name). Null when the user has not yet selected a mode.
 */
enum class StorageMode { LOCAL, SYNCTHING }

/**
 * Persists user preferences using Jetpack DataStore.
 * All reads are exposed as Flows; never blocks the calling coroutine.
 */
class AppPreferencesRepository(private val context: Context) {

    companion object {
        val SYNC_FOLDER_PATH = stringPreferencesKey("sync_folder_path")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val STORAGE_MODE = stringPreferencesKey("storage_mode")

        // Macro targets — daily nutrition goals
        val MACRO_TARGET_CALORIES = intPreferencesKey("macro_target_calories")
        val MACRO_TARGET_PROTEIN = intPreferencesKey("macro_target_protein")
        val MACRO_TARGET_CARBS = intPreferencesKey("macro_target_carbs")
        val MACRO_TARGET_FAT = intPreferencesKey("macro_target_fat")

        // Workout split tracking (Phase 3)
        val LAST_WORKOUT_SPLIT_DAY = stringPreferencesKey("last_workout_split_day")
        val LAST_WORKOUT_DATE = stringPreferencesKey("last_workout_date")
        val DEFAULT_REST_TIMER_SECS = intPreferencesKey("default_rest_timer_secs")

        // User-created exercise library (Phase 3 plan 02)
        val USER_EXERCISES_JSON = stringPreferencesKey("user_exercises_json")
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

    /**
     * The selected storage mode, or null if the user has not yet chosen one.
     * Null is the "not yet decided" sentinel used during onboarding.
     */
    val storageMode: Flow<StorageMode?> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            preferences[STORAGE_MODE]?.let { raw ->
                runCatching { StorageMode.valueOf(raw) }.getOrNull()
            }
        }

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

    suspend fun setStorageMode(mode: StorageMode) {
        context.appDataStore.edit { preferences ->
            preferences[STORAGE_MODE] = mode.name
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

    // -------------------------------------------------------------------------
    // Workout split tracking (Phase 3)
    // -------------------------------------------------------------------------

    /** The split day label of the last completed workout session, or null if no history. */
    val lastWorkoutSplitDay: Flow<String?> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[LAST_WORKOUT_SPLIT_DAY] }

    /** The ISO date string (yyyy-MM-dd) of the last completed workout, or null if no history. */
    val lastWorkoutDate: Flow<String?> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[LAST_WORKOUT_DATE] }

    /** Default rest timer duration in seconds. Defaults to 90 seconds. */
    val defaultRestTimerSecs: Flow<Int> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[DEFAULT_REST_TIMER_SECS] ?: 90 }

    /** Record the most recently completed workout session's split day and date. */
    suspend fun setLastWorkout(splitDay: String, date: String) {
        context.appDataStore.edit { prefs ->
            prefs[LAST_WORKOUT_SPLIT_DAY] = splitDay
            prefs[LAST_WORKOUT_DATE] = date
        }
    }

    /** Update the default rest timer duration. */
    suspend fun setDefaultRestTimerSecs(seconds: Int) {
        context.appDataStore.edit { prefs ->
            prefs[DEFAULT_REST_TIMER_SECS] = seconds
        }
    }

    // -------------------------------------------------------------------------
    // User exercise library (Phase 3 plan 02)
    // -------------------------------------------------------------------------

    /**
     * Raw JSON string for user-created exercises.
     * Empty string = no user exercises. Parsed by OrgWorkoutRepository.
     */
    val userExercisesJson: Flow<String> = context.appDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[USER_EXERCISES_JSON] ?: "" }

    /** Persist user exercises as a JSON string. */
    suspend fun saveUserExercisesJson(json: String) {
        context.appDataStore.edit { prefs ->
            prefs[USER_EXERCISES_JSON] = json
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
