package ru.reset.renplay

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import ru.reset.renplay.di.AppContainer

class RenPlayApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        val uiStyle = container.sharedPreferences.getString("ui_style", "MATERIAL3")
        if (uiStyle == "ONEUI") {
            val themeStr = container.sharedPreferences.getString("theme_option", "SYSTEM")
            when (themeStr) {
                "SYSTEM" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                "LIGHT" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "DARK", "BLACK" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }
}
