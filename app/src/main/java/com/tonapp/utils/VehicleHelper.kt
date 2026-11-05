package com.tonapp.utils

import com.tonapp.model.Vehicle
import java.util.Calendar
_root_ide_package_.com.tonapp.model.Vehicle

object VehicleHelper {

    // Vérifie le statut du Contrôle Technique (Priorité 1)
    // Retourne : 0 = Valide, 1 = Bientôt expiré (moins de 30 jours), 2 = Expiré
    fun getTechnicalControlStatus(vehicle: Vehicle): Int {
        val currentTime = System.currentTimeMillis()
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000

        return when {
            vehicle.technicalControlExpiry < currentTime -> 2 // Expiré
            vehicle.technicalControlExpiry - currentTime <= thirtyDaysInMillis -> 1 // Bientôt expiré
            else -> 0 // Valide
        }
    }
}
