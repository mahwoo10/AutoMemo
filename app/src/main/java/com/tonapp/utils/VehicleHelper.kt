package com.tonapp.utils

import com.tonapp.model.Vehicle

object VehicleHelper {

    fun getTechnicalControlStatus(vehicle: Vehicle): Int {
        val currentTime = System.currentTimeMillis()
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000

        return when {
            vehicle.technicalControlExpiry < currentTime -> 2
            vehicle.technicalControlExpiry - currentTime <= thirtyDaysInMillis -> 1
            else -> 0
        }
    }
}
