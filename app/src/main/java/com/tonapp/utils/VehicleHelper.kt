package com.tonapp.utils

import com.tonapp.model.Vehicle

object VehicleHelper {

    fun getTechnicalControlStatus(vehicle: Vehicle): Int {
        val expiry = vehicle.technicalControlExpiry ?: return 0
        val currentTime = System.currentTimeMillis()
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000

        return when {
            expiry < currentTime -> 2
            expiry - currentTime <= thirtyDaysInMillis -> 1
            else -> 0
        }
    }
}
