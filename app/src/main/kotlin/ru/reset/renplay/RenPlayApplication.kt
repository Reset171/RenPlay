package ru.reset.renplay

import android.app.Application
import ru.reset.renplay.di.AppContainer

class RenPlayApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
