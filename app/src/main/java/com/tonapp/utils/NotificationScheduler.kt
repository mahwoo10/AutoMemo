package com.tonapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.tonapp.NotificationReceiver
import com.tonapp.data.VehicleEntity
import java.util.Calendar

object NotificationScheduler {

    private const val DAY = 24L * 60 * 60 * 1000

    private class Reminder(val days: Int, val offset: Int, val message: String)

    // Les décalages 0, 2, 1 existaient déjà (30, 15, 7 jours) : on les conserve tels quels
    private val reminders = listOf(
        Reminder(30, 0, "⚠️ Contrôle technique expire dans 30 jours !"),
        Reminder(15, 2, "⚠️ Contrôle technique expire dans 15 jours !"),
        Reminder(7, 1, "🚨 URGENT : Contrôle technique expire dans 7 jours !"),
        Reminder(3, 3, "🚨 URGENT : Contrôle technique expire dans 3 jours !"),
        Reminder(0, 4, "🚨 Le contrôle technique expire AUJOURD'HUI !")
    )

    // Un rappel est programmé s'il est activé dans les Paramètres ET sur la fiche du véhicule
    fun scheduleReminders(context: Context, vehicleId: String, vehicleName: String, expiryTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val hour = SettingsPrefs.hour(context)
        val minute = SettingsPrefs.minute(context)
        for (r in reminders) {
            if (!SettingsPrefs.isDayEnabled(context, r.days)) continue
            if (!ReminderPrefs.isEnabled(context, vehicleId, r.days)) continue
            val trigger = atTime(expiryTime - r.days * DAY, hour, minute)
            if (trigger > System.currentTimeMillis()) {
                setAlarm(context, alarmManager, vehicleId.hashCode() + r.offset, vehicleName, r.message, trigger)
            }
        }
    }

    fun cancelReminders(context: Context, vehicleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (r in reminders) {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                vehicleId.hashCode() + r.offset,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    // Après un changement dans les Paramètres : on refait tous les rappels
    fun rescheduleAll(context: Context, vehicles: List<VehicleEntity>) {
        for (v in vehicles) {
            cancelReminders(context, v.id)
            scheduleReminders(context, v.id, v.name, v.technicalControlExpiry)
        }
    }

    // Place l'heure choisie sur le jour concerné
    private fun atTime(time: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
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
