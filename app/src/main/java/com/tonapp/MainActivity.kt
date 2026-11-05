package com.tonapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tonapp.data.AppDatabase
import com.tonapp.utils.VehicleHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAddVehicle = findViewById<Button>(R.id.btnAddVehicle)
        btnAddVehicle.setOnClickListener {
            val intent = Intent(this, AddVehicleActivity::class.java)
            startActivity(intent)
        }

        val database = AppDatabase.getDatabase(this)
        val vehicleDao = database.vehicleDao()

        // Observation de la liste des véhicules (Triée par priorité de contrôle technique)
        lifecycleScope.launch {
            vehicleDao.getAllVehicles().collectLatest { vehicles ->
                if (vehicles.isNotEmpty()) {
                    val firstVehicle = vehicles[0]
                    val status = VehicleHelper.getTechnicalControlStatus(firstVehicle)
                    // Le traitement de l'affichage des alertes se fera ici
                }
            }
        }
    }
}
