package com.sakura

import android.app.Application
import com.sakura.preferences.AppPreferencesRepository

class SakuraApplication : Application() {

    /**
     * Preferences repository is initialized eagerly in onCreate() since it only needs Context.
     * AppContainer is NOT created here — it requires StorageMode from DataStore (async),
     * so it is created in MainActivity after collecting the stored mode.
     */
    lateinit var prefsRepo: AppPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        prefsRepo = AppPreferencesRepository(this)
    }
}
