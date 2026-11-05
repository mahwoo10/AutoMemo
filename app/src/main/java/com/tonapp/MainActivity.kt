package com.tonapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: VehicleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAdd = findViewById<Button>(R.id.btnAddVehicle)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewVehicles)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val tvEmpty = findViewById<TextView>(R.id.tvEmptyState)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        adapter = VehicleAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnAdd.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivity::class.java))
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_add -> {
                    startActivity(Intent(this, AddVehicleActivity::class.java))
                    false
                }
                else -> {
                    Toast.makeText(this, "Bientôt disponible", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@MainActivity).vehicleDao().getAllVehicles().collect { vehicles ->
                adapter.updateData(vehicles)
                adapter.filter(etSearch.text.toString())
                tvEmpty.visibility = if (vehicles.isEmpty()) View.VISIBLE else View.GONE
                updateStats(vehicles)
            }
        }
    }

    private fun updateStats(vehicles: List<VehicleEntity>) {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        var expired = 0
        var soon = 0
        var valid = 0
        for (v in vehicles) {
            val daysRemaining = ((v.technicalControlExpiry - now) / day).toInt()
            when {
                daysRemaining <= 0 -> expired++
                daysRemaining <= 30 -> soon++
                else -> valid++
            }
        }
        findViewById<TextView>(R.id.tvCountExpired).text = expired.toString()
        findViewById<TextView>(R.id.tvCountSoon).text = soon.toString()
        findViewById<TextView>(R.id.tvCountValid).text = valid.toString()
    }
}
