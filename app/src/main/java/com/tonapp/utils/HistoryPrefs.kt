package com.tonapp.utils

import android.content.Context

// Historique léger : mémorise, pour chaque véhicule, les anciennes dates d'échéance
// (sans toucher à la base de données). On garde les 10 plus récentes.
object HistoryPrefs {
    private const val FILE = "automemo_history"
    private const val MAX = 10

    fun get(context: Context, vehicleId: String): List<Long> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(vehicleId, "") ?: ""
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    // Ajoute une ancienne échéance (la plus récente en premier)
    fun add(context: Context, vehicleId: String, oldExpiry: Long) {
        val updated = (listOf(oldExpiry) + get(context, vehicleId)).distinct().take(MAX)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(vehicleId, updated.joinToString(","))
            .apply()
    }

    fun clear(context: Context, vehicleId: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .remove(vehicleId)
            .apply()
    }
}
