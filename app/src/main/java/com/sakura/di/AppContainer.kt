package com.sakura.di

import android.content.Context
import com.sakura.data.food.FoodRepository
import com.sakura.data.food.OrgFoodRepository
import com.sakura.data.workout.OrgWorkoutRepository
import com.sakura.data.workout.WorkoutRepository
import com.sakura.orgengine.OrgParser
import com.sakura.orgengine.OrgWriter
import com.sakura.preferences.AppPreferencesRepository
import com.sakura.preferences.StorageMode
import com.sakura.sync.LocalStorageBackend
import com.sakura.sync.SyncBackend
import com.sakura.sync.SyncthingFileBackend

/**
 * Manual dependency injection container.
 * Constructed in MainActivity after storageMode is resolved from DataStore.
 * Single source of truth for all app-scoped dependencies.
 *
 * @param context     Application context.
 * @param storageMode Determines which SyncBackend implementation is wired in.
 */
class AppContainer(context: Context, storageMode: StorageMode) {

    val prefsRepo = AppPreferencesRepository(context)

    val syncBackend: SyncBackend = when (storageMode) {
        StorageMode.LOCAL -> LocalStorageBackend(context)
        StorageMode.SYNCTHING -> SyncthingFileBackend(prefsRepo)
    }

    /** OrgParser is an object (singleton) — referenced directly. */
    val orgParser = OrgParser

    /** OrgWriter is an object (singleton) — referenced directly. */
    val orgWriter = OrgWriter

    /** Food data repository backed by org files via SyncBackend. */
    val foodRepository: FoodRepository = OrgFoodRepository(syncBackend)

    /** Workout data repository backed by workout-log.org via SyncBackend. */
    val workoutRepository: WorkoutRepository = OrgWorkoutRepository(syncBackend, prefsRepo = prefsRepo)
}
