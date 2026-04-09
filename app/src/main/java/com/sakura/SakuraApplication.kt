package com.sakura

import android.app.Application
import com.sakura.di.AppContainer

class SakuraApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
