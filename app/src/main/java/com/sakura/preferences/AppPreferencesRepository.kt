package com.sakura.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
}
