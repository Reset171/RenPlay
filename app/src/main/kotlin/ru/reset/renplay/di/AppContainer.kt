package ru.reset.renplay.di

import android.content.Context
import android.content.SharedPreferences
import ru.reset.renplay.utils.PREFS_NAME
import java.io.File

class AppContainer(private val context: Context) {
    val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val cacheDir: File
        get() = context.cacheDir
}

