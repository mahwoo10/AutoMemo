package com.tonapp.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

// Réglages généraux : délais de rappel activés, heure des notifications, thème.
object SettingsPrefs {
    private const val FILE = "automemo_settings"

    // Délais proposés (0 = le jour de l'échéance)
    val ALL_DAYS = listOf(30, 15, 7, 3, 0)

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isDayEnabled(context: Context, days: Int): Boolean =
        prefs(context).getBoolean("day_$days", true)

    fun setDayEnabled(context: Context, days: Int, enabled: Boolean) {
        prefs(context).edit().putBoolean("day_$days", enabled).apply()
    }

    // Heure des notifications (9h00 par défaut, comme avant)
    fun hour(context: Context): Int = prefs(context).getInt("hour", 9)
    fun minute(context: Context): Int = prefs(context).getInt("minute", 0)

    fun setTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt("hour", hour).putInt("minute", minute).apply()
    }

    // Thème : suit le téléphone par défaut
    fun themeMode(context: Context): Int =
        prefs(context).getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt("theme", mode).apply()
    }
}
