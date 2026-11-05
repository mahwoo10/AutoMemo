package com.tonapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tonapp.data.AppDatabase
import com.tonapp.utils.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Après un redémarrage, Android efface les alarmes : on les reprogramme.
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val vehicles = AppDatabase.getDatabase(context).vehicleDao().getAllOnce()
                vehicles.forEach {
                    NotificationScheduler.scheduleReminders(
                        context, it.id, it.name, it.technicalControlExpiry
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
