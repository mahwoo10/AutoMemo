package com.tonapp.utils

enum class StatusFilter { ALL, EXPIRED, SOON, VALID }

object VehicleStatus {
    private const val DAY = 24L * 60 * 60 * 1000

    fun daysRemaining(expiry: Long): Int =
        ((expiry - System.currentTimeMillis()) / DAY).toInt()

    // Expiré : 0 jour ou moins | Bientôt : 30 jours ou moins | Valide : au-delà
    fun of(expiry: Long): StatusFilter {
        val days = daysRemaining(expiry)
        return when {
            days <= 0 -> StatusFilter.EXPIRED
            days <= 30 -> StatusFilter.SOON
            else -> StatusFilter.VALID
        }
    }
}
