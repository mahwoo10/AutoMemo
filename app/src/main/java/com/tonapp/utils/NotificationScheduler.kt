package com.tonapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.tonapp.NotificationReceiver

object NotificationScheduler {

    fun scheduleReminders(context: Context, vehicleId: String, vehicleName: String, expiryTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Alerte 30 jours avant
        val timeJ30 = expiryTime - (30L * 24 * 60 * 60 * 1000)
        if (timeJ30 > System.currentTimeMillis()) {
            setAlarm(context, alarmManager, vehicleId.hashCode(), vehicleName, "⚠️ Contrôle technique expire dans 30 jours !", timeJ30)
        }

        // Alerte 7 jours avant
        val timeJ7 = expiryTime - (7L * 24 * 60 * 60 * 1000)
        if (timeJ7 > System.currentTimeMillis()) {
            setAlarm(context, alarmManager, vehicleId.hashCode() + 1, vehicleName, "🚨 URGENT : Contrôle technique expire dans 7 jours !", timeJ7)
        }
    }

    private fun setAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int, vehicleName: String, message: String, triggerAtMillis: Long) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("VEHICLE_NAME", vehicleName)
            putExtra("MESSAGE", message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } catch (e: Exception) {
            // Ignorer si les permissions d'alarme exacte sont restreintes par le système
        }
    }
}
