package com.sakura.di

import android.content.Context

/**
 * Manual dependency injection container. Wired in SakuraApplication.onCreate().
 * Add repositories and backends here as they are implemented in subsequent plans.
 */
class AppContainer(context: Context) {

    // TODO: Uncomment once AppPreferencesRepository is implemented (Plan 01-02)
    // val prefsRepo = AppPreferencesRepository(context)

    // Placeholders for future plans:
    // val syncBackend: SyncBackend
    // val orgParser: OrgParser
    // val orgWriter: OrgWriter
}
