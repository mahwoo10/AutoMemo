package com.tonapp

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import com.tonapp.utils.HistoryPrefs
import com.tonapp.utils.NotificationScheduler
import com.tonapp.utils.ReminderPrefs
import com.tonapp.utils.SettingsPrefs
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
    private var vehicle: VehicleEntity? = null

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Sélection d'un nouveau justificatif (PDF ou photo)
    private val docLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // Certaines sources ne proposent pas de permission persistante
                }
                saveDocument(uri.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_detail)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        vehicleId = intent.getStringExtra("ID") ?: ""
        vehicleName = intent.getStringExtra("NAME") ?: "Véhicule"
        currentExpiry = intent.getLongExtra("EXPIRY", System.currentTimeMillis())

        // Premier affichage avec les données transmises par la liste
        findViewById<TextView>(R.id.tvDetailTitle).text = vehicleName
        findViewById<TextView>(R.id.tvDetailPlate).text = intent.getStringExtra("PLATE") ?: ""
        showExpiryAndStatus()
        bindDocument(intent.getStringExtra("DOC_PATH"))
        showHistory()
        setupReminderSwitches()
        setupVisitDone()

        findViewById<View>(R.id.btnEditVehicle).setOnClickListener { showEditDialog() }
        findViewById<View>(R.id.btnChangeDoc).setOnClickListener { pickDocument() }

        // Puis chargement des données complètes (propriétaire, téléphone…)
        if (vehicleId.isNotEmpty()) {
            lifecycleScope.launch {
                AppDatabase.getDatabase(this@VehicleDetailActivity).vehicleDao().getById(vehicleId)?.let {
                    vehicle = it
                    bindVehicle(it)
                }
            }
        } else {
            findViewById<View>(R.id.btnEditVehicle).visibility = View.GONE
            findViewById<View>(R.id.btnChangeDoc).visibility = View.GONE
        }
    }

    // Affiche toutes les informations d'un véhicule
    private fun bindVehicle(v: VehicleEntity) {
        vehicleName = v.name
        currentExpiry = v.technicalControlExpiry
        findViewById<TextView>(R.id.tvDetailTitle).text = v.name
        findViewById<TextView>(R.id.tvDetailPlate).text = v.licensePlate

        val lines = mutableListOf<String>()
        if (v.ownerFullName.isNotBlank()) lines.add("Propriétaire : ${v.ownerFullName}")
        if (v.ownerPhoneNumber.isNotBlank()) lines.add("Téléphone : ${v.ownerPhoneNumber}")
        findViewById<TextView>(R.id.tvDetailOwner).text =
            if (lines.isEmpty()) "Aucune information renseignée" else lines.joinToString("\n")

        showExpiryAndStatus()
        bindDocument(v.technicalControlPdfPath)
        showHistory()
    }

    // Date d'échéance + bandeau de statut, à partir de currentExpiry
    private fun showExpiryAndStatus() {
        val expiry = currentExpiry
        findViewById<TextView>(R.id.tvDetailExpiry).text = "Expire le : ${dateFormat.format(Date(expiry))}"

        val card = findViewById<CardView>(R.id.cardGlobalStatus)
        val tv = findViewById<TextView>(R.id.tvGlobalStatus)
        val status = VehicleStatus.of(expiry)

        val text: String
        val fg: Int
        val bg: Int
        when (status) {
            StatusFilter.EXPIRED -> {
                text = "Statut Global : ${VehicleStatus.remainingLabel(expiry)}"
                fg = R.color.status_red; bg = R.color.status_red_bg
            }
            StatusFilter.TODAY -> {
                text = "Statut Global : Échéance aujourd'hui"
                fg = R.color.status_orange; bg = R.color.status_orange_bg
            }
            StatusFilter.SOON -> {
                text = "Statut Global : Expire bientôt (${VehicleStatus.calendarDaysUntil(expiry)} j)"
                fg = R.color.status_orange; bg = R.color.status_orange_bg
            }
            else -> {
                text = "Statut Global : À jour"
                fg = R.color.status_green; bg = R.color.status_green_bg
            }
        }
        tv.text = text
        tv.setTextColor(ContextCompat.getColor(this, fg))
        card.setCardBackgroundColor(ContextCompat.getColor(this, bg))
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

    // ---------- Justificatif ----------

    private fun bindDocument(docPath: String?) {
        val btnViewDoc = findViewById<Button>(R.id.btnViewDoc)
        val btnChangeDoc = findViewById<Button>(R.id.btnChangeDoc)

        if (docPath.isNullOrEmpty()) {
            btnViewDoc.visibility = View.GONE
            btnChangeDoc.text = "Joindre un justificatif"
            return
        }
        val uri = Uri.parse(docPath)
        val mimeType = getMimeType(this, uri)
        val isPdf = mimeType.contains("pdf", ignoreCase = true)

        btnViewDoc.visibility = View.VISIBLE
        btnViewDoc.text = if (isPdf) "📄 Afficher le PDF" else "🖼️ Afficher la Photo"
        btnViewDoc.setOnClickListener {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            try {
                startActivity(viewIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Impossible d'ouvrir le justificatif", Toast.LENGTH_SHORT).show()
            }
        }
        btnChangeDoc.text = "Remplacer le justificatif"
    }

    private fun pickDocument() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        docLauncher.launch(pick)
    }

    private fun saveDocument(path: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(this@VehicleDetailActivity).vehicleDao()
            val v = dao.getById(vehicleId) ?: return@launch
            val updated = v.copy(technicalControlPdfPath = path)
            dao.insertVehicle(updated)
            vehicle = updated
            bindDocument(path)
            Toast.makeText(this@VehicleDetailActivity, "Justificatif enregistré", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Visite technique effectuée ----------

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
            val v = dao.getById(vehicleId)
            if (v == null) {
                Toast.makeText(this@VehicleDetailActivity, "Véhicule introuvable", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val updated = v.copy(technicalControlExpiry = newExpiry)
            dao.insertVehicle(updated)
            HistoryPrefs.add(this@VehicleDetailActivity, vehicleId, v.technicalControlExpiry)

            // Supprime les anciens rappels puis reprogramme selon la nouvelle date
            NotificationScheduler.cancelReminders(this@VehicleDetailActivity, vehicleId)
            NotificationScheduler.scheduleReminders(this@VehicleDetailActivity, vehicleId, updated.name, newExpiry)

            vehicle = updated
            bindVehicle(updated)
            Toast.makeText(
                this@VehicleDetailActivity,
                "Nouvelle échéance : ${dateFormat.format(Date(newExpiry))}",
                Toast.LENGTH_LONG
            ).show()

            // L'ancien justificatif correspond à l'ancienne visite : on propose d'en joindre un nouveau
            AlertDialog.Builder(this@VehicleDetailActivity)
                .setTitle("Nouveau justificatif ?")
                .setMessage("Voulez-vous joindre le justificatif de cette nouvelle visite ?")
                .setPositiveButton("Joindre") { _, _ -> pickDocument() }
                .setNegativeButton("Plus tard", null)
                .show()
        }
    }

    // ---------- Modification des informations ----------

    private fun showEditDialog() {
        val v = vehicle ?: return
        val view = layoutInflater.inflate(R.layout.dialog_edit_vehicle, null)
        val etPlate = view.findViewById<EditText>(R.id.etEditPlate)
        val etName = view.findViewById<EditText>(R.id.etEditName)
        val etOwner = view.findViewById<EditText>(R.id.etEditOwner)
        val etPhone = view.findViewById<EditText>(R.id.etEditPhone)
        val btnDate = view.findViewById<Button>(R.id.btnEditDate)

        etPlate.setText(v.licensePlate)
        etName.setText(if (v.name == v.licensePlate) "" else v.name)
        etOwner.setText(v.ownerFullName)
        etPhone.setText(v.ownerPhoneNumber)

        var newExpiry = v.technicalControlExpiry
        btnDate.text = "Échéance : ${dateFormat.format(Date(newExpiry))}"
        btnDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = newExpiry }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply { set(year, month, day, 23, 59, 0) }
                    newExpiry = picked.timeInMillis
                    btnDate.text = "Échéance : ${dateFormat.format(Date(newExpiry))}"
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Modifier la fiche")
            .setView(view)
            .setPositiveButton("Enregistrer", null)
            .setNegativeButton("Annuler", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val plate = etPlate.text.toString().trim()
                if (plate.isEmpty()) {
                    etPlate.error = "Obligatoire"
                    return@setOnClickListener
                }
                val updated = v.copy(
                    licensePlate = plate,
                    name = etName.text.toString().trim().ifEmpty { plate },
                    ownerFullName = etOwner.text.toString().trim(),
                    ownerPhoneNumber = etPhone.text.toString().trim(),
                    technicalControlExpiry = newExpiry
                )
                dialog.dismiss()
                saveEdits(updated)
            }
        }
        dialog.show()
    }

    private fun saveEdits(updated: VehicleEntity) {
        lifecycleScope.launch {
            AppDatabase.getDatabase(this@VehicleDetailActivity).vehicleDao().insertVehicle(updated)
            // Nom ou date modifiés : les rappels sont refaits (corriger une date n'ajoute rien à l'historique)
            NotificationScheduler.cancelReminders(this@VehicleDetailActivity, vehicleId)
            NotificationScheduler.scheduleReminders(
                this@VehicleDetailActivity, vehicleId, updated.name, updated.technicalControlExpiry
            )
            vehicle = updated
            bindVehicle(updated)
            Toast.makeText(this@VehicleDetailActivity, "Fiche mise à jour", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Rappels de ce véhicule ----------

    private fun setupReminderSwitches() {
        val defs = listOf(
            30 to R.id.switch30Days,
            15 to R.id.switch15Days,
            7 to R.id.switch7Days,
            3 to R.id.switch3Days,
            0 to R.id.switchDay0
        )
        var visibleCount = 0

        for ((days, id) in defs) {
            val sw = findViewById<SwitchCompat>(id)

            // Les délais désactivés dans les Paramètres ne sont pas proposés ici
            if (!SettingsPrefs.isDayEnabled(this, days)) {
                sw.visibility = View.GONE
                continue
            }
            visibleCount++

            if (vehicleId.isEmpty()) {
                sw.isEnabled = false
                continue
            }

            // État enregistré (activé par défaut), avant d'attacher l'écouteur
            sw.isChecked = ReminderPrefs.isEnabled(this, vehicleId, days)
            sw.setOnCheckedChangeListener { _, checked ->
                ReminderPrefs.setEnabled(this, vehicleId, days, checked)
                // On reprogramme tous les rappels selon les interrupteurs (date et nom à jour)
                NotificationScheduler.cancelReminders(this, vehicleId)
                NotificationScheduler.scheduleReminders(this, vehicleId, vehicleName, currentExpiry)
                val label = if (days == 0) "Rappel du jour J" else "Rappel $days jours"
                Toast.makeText(
                    this,
                    if (checked) "$label activé" else "$label désactivé",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        findViewById<View>(R.id.tvRemindersEmpty).visibility =
            if (visibleCount == 0) View.VISIBLE else View.GONE
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
