package com.tonapp

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import com.tonapp.utils.PdfManagerHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddVehicleActivity : AppCompatActivity() {

    private var selectedPdfUri: String? = null
    private var selectedExpiryTime: Long? = null

    private lateinit var tvPdfStatus: TextView
    private lateinit var tvSelectedDate: TextView

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedPdfUri = uri.toString()
                tvPdfStatus.text = "PDF attaché : ${uri.lastPathSegment}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_vehicle)

        val etVehicleName = findViewById<EditText>(R.id.etVehicleName)
        val etLicensePlate = findViewById<EditText>(R.id.etLicensePlate)
        val etOwnerName = findViewById<EditText>(R.id.etOwnerName)
        val etOwnerPhone = findViewById<EditText>(R.id.etOwnerPhone)
        val btnPickExpiryDate = findViewById<Button>(R.id.btnPickExpiryDate)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        val btnSelectPdf = findViewById<Button>(R.id.btnSelectPdf)
        tvPdfStatus = findViewById(R.id.tvPdfStatus)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // Sélecteur de date d'expiration
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

        btnSelectPdf.setOnClickListener {
            PdfManagerHelper.openPdfSelector(pdfLauncher)
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

            val vehicleEntity = VehicleEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                licensePlate = plate,
                ownerFullName = ownerName,
                ownerPhoneNumber = ownerPhone,
                ownerEmail = "",
                ownerAddress = "",
                technicalControlExpiry = expiry,
                technicalControlPdfPath = selectedPdfUri,
                tvmExpiry = null,
                tvmReceiptPdfPath = null
            )

            lifecycleScope.launch {
                AppDatabase.getDatabase(this@AddVehicleActivity).vehicleDao().insertVehicle(vehicleEntity)
                Toast.makeText(this@AddVehicleActivity, "Document enregistré avec succès !", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
