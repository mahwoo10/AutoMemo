package com.tonapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tonapp.data.AppDatabase
import com.tonapp.utils.VehicleHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Utilisation d'un layout simple ou création programmatique pour l'instant
        setContentView(R.layout.activity_main)

        val database = AppDatabase.getDatabase(this)
        val vehicleDao = database.vehicleDao()

        // Observation de la liste des véhicules (Triée par priorité de contrôle technique)
        lifecycleScope.launch {
            vehicleDao.getAllVehicles().collectLatest { vehicles ->
                if (vehicles.isNotEmpty()) {
                    val firstVehicle = vehicles[0]
                    val status = VehicleHelper.getTechnicalControlStatus(firstVehicle)
                    
                    // Exemple de traitement du statut prioritaire du contrôle technique
                    when (status) {
                        2 -> Toast.applicationContext() // Alerte Expiré
                        1 -> Toast.applicationContext() // Bientôt expiré
                        else -> {}
                    }
                }
            }
        }
    }
}
