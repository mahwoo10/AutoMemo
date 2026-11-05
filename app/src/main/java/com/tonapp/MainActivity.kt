package com.tonapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapp.data.AppDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAddVehicle = findViewById<Button>(R.id.btnAddVehicle)
        btnAddVehicle.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivity::class.java))
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewVehicles)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val vehicleDao = AppDatabase.getDatabase(this).vehicleDao()

        lifecycleScope.launch {
            vehicleDao.getAllVehicles().collectLatest { vehicles ->
                recyclerView.adapter = VehicleAdapter(vehicles)
            }
        }
    }
}
