package com.tonapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import java.util.UUID

class AddVehicleActivity : AppCompatActivity() {

    private var selectedPdfUri: String? = null
    private lateinit var tvPdfStatus: TextView

    // Gestionnaire pour récupérer le résultat du sélecteur de PDF
    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Persistance des droits d'accès au fichier URI si besoin
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedPdfUri = uri.toString()
                tvPdfStatus.text = "PDF sélectionné : ${uri.lastPathSegment}"
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
        val etOwnerEmail = findViewById<EditText>(R.id.etOwnerEmail)
        val etOwnerAddress = findViewById<EditText>(R.id.etOwnerAddress)
        val btnSelectPdf = findViewById<Button>(R.id.btnSelectPdf)
        tvPdfStatus = findViewById<TextView>(R.id.tvPdfStatus)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // Bouton pour lancer l'import du PDF du Contrôle Technique
        btnSelectPdf.setOnClickListener {
            PdfManagerHelper.openPdfSelector(pdfLauncher)
        }

        // Enregistrement dans Room
        btnSave.setOnClickListener {
            val name = etVehicleName.text.toString().trim()
            val plate = etLicensePlate.text.toString().trim()
            val ownerName = etOwnerName.text.toString().trim()
            val ownerPhone = etOwnerPhone.text.toString().trim()
            val ownerEmail = etOwnerEmail.text.toString().trim()
            val ownerAddress = etOwnerAddress.text.toString().trim()

            if (name.isEmpty() || plate.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir au moins le nom et l'immatriculation", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Création de l'entité (on met une date fictive ou actuelle pour l'exemple du contrôle technique)
            val vehicleEntity = VehicleEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                licensePlate = plate,
                ownerFullName = ownerName,
                ownerPhoneNumber = ownerPhone,
                ownerEmail = ownerEmail,
                ownerAddress = ownerAddress,
                technicalControlExpiry = System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000), // +90 jours par défaut
                technicalControlPdfPath = selectedPdfUri,
                tvmExpiry = null,
                tvmReceiptPdfPath = null
            )

            lifecycleScope.launch {
                AppDatabase.getDatabase(this@AddVehicleActivity).vehicleDao().insertVehicle(vehicleEntity)
                Toast.makeText(this@AddVehicleActivity, "Véhicule et contrôle technique enregistrés !", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
