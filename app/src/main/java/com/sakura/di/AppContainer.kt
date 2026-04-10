package com.sakura.di

import android.content.Context
import com.sakura.orgengine.OrgParser
import com.sakura.orgengine.OrgWriter
import com.sakura.preferences.AppPreferencesRepository
import com.sakura.sync.SyncBackend
import com.sakura.sync.SyncthingFileBackend

/**
 * Manual dependency injection container. Wired in SakuraApplication.onCreate().
 * Single source of truth for all app-scoped dependencies.
 */
class AppContainer(context: Context) {

    val prefsRepo = AppPreferencesRepository(context)

    val syncBackend: SyncBackend = SyncthingFileBackend(prefsRepo)

    /** OrgParser is an object (singleton) — referenced directly. */
    val orgParser = OrgParser

    /** OrgWriter is an object (singleton) — referenced directly. */
    val orgWriter = OrgWriter
}
