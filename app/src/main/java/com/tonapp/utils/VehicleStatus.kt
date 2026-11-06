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

    // Texte du badge pour un véhicule expiré
    fun expiredLabel(expiry: Long): String {
        val now = System.currentTimeMillis()
        if (expiry > now) return "Expire aujourd'hui"
        val days = ((now - expiry) / DAY).toInt()
        return when {
            days < 1 -> "Expiré aujourd'hui"
            days == 1 -> "Expiré depuis 1 jour"
            days < 30 -> "Expiré depuis $days jours"
            days < 365 -> "Expiré depuis ${days / 30} mois"
            days < 730 -> "Expiré depuis 1 an"
            else -> "Expiré depuis ${days / 365} ans"
        }
    }
}
