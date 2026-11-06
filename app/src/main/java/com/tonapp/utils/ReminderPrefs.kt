package com.tonapp.utils

import android.content.Context

// Mémorise, pour chaque véhicule, quels rappels (30, 15, 7 jours) sont activés.
// Par défaut, tous les rappels sont activés.
object ReminderPrefs {
    private const val FILE = "automemo_reminders"
    private val allDays = listOf(30, 15, 7)

    private fun key(vehicleId: String, days: Int) = "$vehicleId:$days"

    fun isEnabled(context: Context, vehicleId: String, days: Int): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(key(vehicleId, days), true)

    fun setEnabled(context: Context, vehicleId: String, days: Int, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key(vehicleId, days), enabled)
            .apply()
    }

    fun clear(context: Context, vehicleId: String) {
        val editor = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        allDays.forEach { editor.remove(key(vehicleId, it)) }
        editor.apply()
    }
}
