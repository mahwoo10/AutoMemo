package com.tonapp

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import com.tonapp.utils.NotificationScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddVehicleActivity : AppCompatActivity() {

    private var selectedDocUri: String? = null
    private var selectedExpiryTime: Long? = null

    private lateinit var tvPdfStatus: TextView
    private lateinit var tvSelectedDate: TextView

    private val docLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // Les images de galerie n'ont pas systématiquement besoin de permission persistante
                }
                selectedDocUri = uri.toString()
                tvPdfStatus.text = "Document attaché : ${uri.lastPathSegment}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_vehicle)

        checkNotificationPermission()

        val etVehicleName = findViewById<EditText>(R.id.etVehicleName)
        val etLicensePlate = findViewById<EditText>(R.id.etLicensePlate)
        val etOwnerName = findViewById<EditText>(R.id.etOwnerName)
        val etOwnerPhone = findViewById<EditText>(R.id.etOwnerPhone)
        val btnPickExpiryDate = findViewById<Button>(R.id.btnPickExpiryDate)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        val btnSelectPhoto = findViewById<Button>(R.id.btnSelectPhoto)
        val btnSelectPdf = findViewById<Button>(R.id.btnSelectPdf)
        tvPdfStatus = findViewById(R.id.tvPdfStatus)
        val btnSave = findViewById<Button>(R.id.btnSave)

        btnPickExpiryDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedExpiryTime = calendar.timeInMillis
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    tvSelectedDate.text = "Date d'expiration : ${dateFormat.format(calendar.time)}"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        btnSelectPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            docLauncher.launch(intent)
        }

        btnSelectPdf.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            docLauncher.launch(intent)
        }

        btnSave.setOnClickListener {
            val name = etVehicleName.text.toString().trim()
            val plate = etLicensePlate.text.toString().trim()
            val ownerName = etOwnerName.text.toString().trim()
            val ownerPhone = etOwnerPhone.text.toString().trim()

            if (name.isEmpty() || plate.isEmpty()) {
                Toast.makeText(this, "Veuillez renseigner le nom et l'immatriculation", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val expiry = selectedExpiryTime ?: (System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000))
            val vehicleId = UUID.randomUUID().toString()

            val vehicleEntity = VehicleEntity(
                id = vehicleId,
                name = name,
                licensePlate = plate,
                ownerFullName = ownerName,
                ownerPhoneNumber = ownerPhone,
                ownerEmail = "",
                ownerAddress = "",
                technicalControlExpiry = expiry,
                technicalControlPdfPath = selectedDocUri,
                tvmExpiry = null,
                tvmReceiptPdfPath = null
            )

            lifecycleScope.launch {
                AppDatabase.getDatabase(this@AddVehicleActivity).vehicleDao().insertVehicle(vehicleEntity)

                // Programmation automatique des rappels J-30 et J-7
                NotificationScheduler.scheduleReminders(this@AddVehicleActivity, vehicleId, name, expiry)

                Toast.makeText(this@AddVehicleActivity, "Document et rappels enregistrés !", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
