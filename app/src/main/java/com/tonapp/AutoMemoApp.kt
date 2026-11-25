package com.tonapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.tonapp.utils.SettingsPrefs

class AutoMemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(SettingsPrefs.themeMode(this))
    }
}
