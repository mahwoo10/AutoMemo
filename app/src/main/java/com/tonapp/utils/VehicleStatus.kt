package com.tonapp.utils

import java.util.Calendar

enum class StatusFilter { ALL, UPCOMING, EXPIRED, TODAY, SOON, VALID }

object VehicleStatus {
    private const val DAY = 24L * 60 * 60 * 1000

    private fun startOfDay(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Ancien calcul (en tranches de 24 h), conservé pour les écrans existants
    fun daysRemaining(expiry: Long): Int =
        ((expiry - System.currentTimeMillis()) / DAY).toInt()

    // Nombre de jours calendaires jusqu'à l'échéance : 0 = aujourd'hui, négatif = dépassée
    fun calendarDaysUntil(expiry: Long): Int {
        val diff = startOfDay(expiry) - startOfDay(System.currentTimeMillis())
        return Math.round(diff / DAY.toDouble()).toInt()
    }

    // Dépassée : avant aujourd'hui | Aujourd'hui | Bientôt : 30 jours ou moins | Valide : au-delà
    fun of(expiry: Long): StatusFilter {
        val days = calendarDaysUntil(expiry)
        return when {
            days < 0 -> StatusFilter.EXPIRED
            days == 0 -> StatusFilter.TODAY
            days <= 30 -> StatusFilter.SOON
            else -> StatusFilter.VALID
        }
    }

    // Texte court du temps restant (ou du retard) affiché sur la carte
    fun remainingLabel(expiry: Long): String {
        val days = calendarDaysUntil(expiry)
        return when {
            days < 0 -> {
                val late = -days
                when {
                    late == 1 -> "Retard de 1 jour"
                    late < 60 -> "Retard de $late jours"
                    late < 730 -> "Retard de ${late / 30} mois"
                    else -> "Retard de ${late / 365} ans"
                }
            }
            days == 0 -> "Aujourd'hui"
            days == 1 -> "Demain"
            days <= 60 -> "Dans $days jours"
            days < 730 -> "Dans ${days / 30} mois"
            else -> "Dans ${days / 365} ans"
        }
    }

    // Texte du badge pour un véhicule expiré (utilisé par la fiche détail)
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
