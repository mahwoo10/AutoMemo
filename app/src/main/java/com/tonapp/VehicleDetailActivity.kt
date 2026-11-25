package com.tonapp

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.tonapp.data.AppDatabase
import com.tonapp.utils.HistoryPrefs
import com.tonapp.utils.NotificationScheduler
import com.tonapp.utils.ReminderPrefs
import com.tonapp.utils.StatusFilter
import com.tonapp.utils.VehicleStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class VehicleDetailActivity : AppCompatActivity() {

    private lateinit var vehicleId: String
    private lateinit var vehicleName: String
    private var currentExpiry: Long = 0L

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_detail)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        vehicleId = intent.getStringExtra("ID") ?: ""
        vehicleName = intent.getStringExtra("NAME") ?: "Véhicule"
        val plate = intent.getStringExtra("PLATE") ?: ""
        currentExpiry = intent.getLongExtra("EXPIRY", System.currentTimeMillis())
        val docPath = intent.getStringExtra("DOC_PATH")

        findViewById<TextView>(R.id.tvDetailTitle).text = vehicleName
        findViewById<TextView>(R.id.tvDetailPlate).text = plate

        showExpiryAndStatus()
        showHistory()
        setupReminderSwitches()
        setupVisitDone()

        val btnViewDoc = findViewById<Button>(R.id.btnViewDoc)
        if (!docPath.isNullOrEmpty()) {
            val uri = Uri.parse(docPath)
            val mimeType = getMimeType(this, uri)
            val isPdf = mimeType.contains("pdf", ignoreCase = true)

            btnViewDoc.text = if (isPdf) "📄 Afficher le PDF" else "🖼️ Afficher la Photo"

            btnViewDoc.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Impossible d'ouvrir le justificatif", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnViewDoc.visibility = View.GONE
        }
    }

    // Date d'échéance + bandeau de statut, à partir de currentExpiry
    private fun showExpiryAndStatus() {
        val expiry = currentExpiry
        findViewById<TextView>(R.id.tvDetailExpiry).text = "Expire le : ${dateFormat.format(Date(expiry))}"

        val cardGlobalStatus = findViewById<CardView>(R.id.cardGlobalStatus)
        val tvGlobalStatus = findViewById<TextView>(R.id.tvGlobalStatus)
        val status = VehicleStatus.of(expiry)
        val daysRemaining = VehicleStatus.calendarDaysUntil(expiry)

        when (status) {
            StatusFilter.EXPIRED -> {
                tvGlobalStatus.text = "Statut Global : ${VehicleStatus.remainingLabel(expiry)}"
                tvGlobalStatus.setTextColor(Color.parseColor("#D32F2F"))
                cardGlobalStatus.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
            }
            StatusFilter.TODAY -> {
                tvGlobalStatus.text = "Statut Global : Échéance aujourd'hui"
                tvGlobalStatus.setTextColor(Color.parseColor("#B45309"))
                cardGlobalStatus.setCardBackgroundColor(Color.parseColor("#FEF3C7"))
            }
            StatusFilter.SOON -> {
                tvGlobalStatus.text = "Statut Global : Expire bientôt ($daysRemaining j)"
                tvGlobalStatus.setTextColor(Color.parseColor("#B45309"))
                cardGlobalStatus.setCardBackgroundColor(Color.parseColor("#FEF3C7"))
            }
            else -> {
                tvGlobalStatus.text = "Statut Global : À jour"
                tvGlobalStatus.setTextColor(Color.parseColor("#2E7D32"))
                cardGlobalStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
            }
        }
    }

    // Historique léger des anciennes échéances (masqué s'il est vide)
    private fun showHistory() {
        val layoutHistory = findViewById<View>(R.id.layoutHistory)
        val old = if (vehicleId.isEmpty()) emptyList() else HistoryPrefs.get(this, vehicleId)
        if (old.isEmpty()) {
            layoutHistory.visibility = View.GONE
            return
        }
        findViewById<TextView>(R.id.tvHistory).text =
            old.joinToString("\n") { "Échéance du ${dateFormat.format(Date(it))}" }
        layoutHistory.visibility = View.VISIBLE
    }

    // « Visite technique effectuée » : nouvelle date, rappels réinitialisés
    private fun setupVisitDone() {
        val btn = findViewById<Button>(R.id.btnVisitDone)
        if (vehicleId.isEmpty()) {
            btn.isEnabled = false
            return
        }
        btn.setOnClickListener {
            // Par défaut, la date proposée est dans un an
            val cal = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply { set(year, month, day, 23, 59, 0) }
                    if (picked.timeInMillis <= System.currentTimeMillis()) {
                        Toast.makeText(this, "La nouvelle échéance doit être dans le futur", Toast.LENGTH_SHORT).show()
                    } else {
                        renewExpiry(picked.timeInMillis)
                    }
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun renewExpiry(newExpiry: Long) {
        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(this@VehicleDetailActivity).vehicleDao()
            val vehicle = dao.getById(vehicleId)
            if (vehicle == null) {
                Toast.makeText(this@VehicleDetailActivity, "Véhicule introuvable", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val oldExpiry = vehicle.technicalControlExpiry

            // Enregistre la nouvelle date (seul champ modifié)
            dao.insertVehicle(vehicle.copy(technicalControlExpiry = newExpiry))
            HistoryPrefs.add(this@VehicleDetailActivity, vehicleId, oldExpiry)

            // Supprime les anciens rappels puis reprogramme selon la nouvelle date
            NotificationScheduler.cancelReminders(this@VehicleDetailActivity, vehicleId)
            NotificationScheduler.scheduleReminders(this@VehicleDetailActivity, vehicleId, vehicle.name, newExpiry)

            currentExpiry = newExpiry
            showExpiryAndStatus()
            showHistory()
            Toast.makeText(
                this@VehicleDetailActivity,
                "Nouvelle échéance : ${dateFormat.format(Date(newExpiry))}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupReminderSwitches() {
        val switches = listOf(
            30 to findViewById<SwitchCompat>(R.id.switch30Days),
            15 to findViewById<SwitchCompat>(R.id.switch15Days),
            7 to findViewById<SwitchCompat>(R.id.switch7Days)
        )

        if (vehicleId.isEmpty()) {
            switches.forEach { it.second.isEnabled = false }
            return
        }

        // État enregistré (activé par défaut), avant d'attacher les écouteurs
        switches.forEach { (days, sw) ->
            sw.isChecked = ReminderPrefs.isEnabled(this, vehicleId, days)
        }

        switches.forEach { (days, sw) ->
            sw.setOnCheckedChangeListener { _, checked ->
                ReminderPrefs.setEnabled(this, vehicleId, days, checked)
                // On reprogramme tous les rappels selon les interrupteurs (date courante)
                NotificationScheduler.cancelReminders(this, vehicleId)
                NotificationScheduler.scheduleReminders(this, vehicleId, vehicleName, currentExpiry)
                Toast.makeText(
                    this,
                    if (checked) "Rappel $days jours activé" else "Rappel $days jours désactivé",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getMimeType(context: Context, uri: Uri): String {
        val typeFromResolver = try {
            context.contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }
        return typeFromResolver ?: if (uri.toString().contains(".pdf", ignoreCase = true)) "application/pdf" else "image/*"
    }
}
