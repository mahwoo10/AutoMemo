package com.tonapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.tonapp.NotificationReceiver
import java.util.Calendar

object NotificationScheduler {

    private const val DAY = 24L * 60 * 60 * 1000

    // (jours avant l'échéance, décalage du code de requête, message)
    private val reminders = listOf(
        Triple(30, 0, "⚠️ Contrôle technique expire dans 30 jours !"),
        Triple(15, 2, "⚠️ Contrôle technique expire dans 15 jours !"),
        Triple(7, 1, "🚨 URGENT : Contrôle technique expire dans 7 jours !")
    )

    fun scheduleReminders(context: Context, vehicleId: String, vehicleName: String, expiryTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for ((days, offset, message) in reminders) {
            val trigger = atNineAm(expiryTime - days * DAY)
            if (trigger > System.currentTimeMillis()) {
                setAlarm(context, alarmManager, vehicleId.hashCode() + offset, vehicleName, message, trigger)
            }
        }
    }

    fun cancelReminders(context: Context, vehicleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for ((_, offset, _) in reminders) {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                vehicleId.hashCode() + offset,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    // Les rappels sonnent à 9h00 le jour concerné
    private fun atNineAm(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun setAlarm(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        vehicleName: String,
        message: String,
        triggerAtMillis: Long
    ) {
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
            // Ignoré si le système restreint les alarmes
        }
    }
}
