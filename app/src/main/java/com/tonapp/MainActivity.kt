package com.tonapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import com.tonapp.utils.StatusFilter
import com.tonapp.utils.VehicleStatus
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: VehicleAdapter
    private lateinit var tvEmpty: TextView
    private var totalCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAdd = findViewById<View>(R.id.btnAddVehicle)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewVehicles)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupStatus)
        tvEmpty = findViewById(R.id.tvEmptyState)

        adapter = VehicleAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnAdd.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivity::class.java))
        }

        // Filtre par statut (puces)
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipExpired -> StatusFilter.EXPIRED
                R.id.chipSoon -> StatusFilter.SOON
                R.id.chipValid -> StatusFilter.VALID
                else -> StatusFilter.ALL
            }
            adapter.setStatusFilter(filter)
            refreshEmptyState()
        }

        // Toucher un compteur sélectionne la puce correspondante
        findViewById<View>(R.id.statExpired).setOnClickListener { chipGroup.check(R.id.chipExpired) }
        findViewById<View>(R.id.statSoon).setOnClickListener { chipGroup.check(R.id.chipSoon) }
        findViewById<View>(R.id.statValid).setOnClickListener { chipGroup.check(R.id.chipValid) }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
                refreshEmptyState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@MainActivity).vehicleDao().getAllVehicles().collect { vehicles ->
                totalCount = vehicles.size
                adapter.updateData(vehicles)
                adapter.filter(etSearch.text.toString())
                refreshEmptyState()
                updateStats(vehicles)
            }
        }
    }

    private fun refreshEmptyState() {
        if (adapter.itemCount == 0) {
            tvEmpty.text = if (totalCount == 0) {
                "Aucun véhicule pour l'instant\nAppuyez sur + pour en ajouter un"
            } else {
                "Aucun véhicule dans cette liste"
            }
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
    }

    private fun updateStats(vehicles: List<VehicleEntity>) {
        var expired = 0
        var soon = 0
        var valid = 0
        for (v in vehicles) {
            when (VehicleStatus.of(v.technicalControlExpiry)) {
                StatusFilter.EXPIRED -> expired++
                StatusFilter.SOON -> soon++
                else -> valid++
            }
        }
        findViewById<TextView>(R.id.tvCountExpired).text = expired.toString()
        findViewById<TextView>(R.id.tvCountSoon).text = soon.toString()
        findViewById<TextView>(R.id.tvCountValid).text = valid.toString()

        findViewById<Chip>(R.id.chipAll).text = "Tous (${vehicles.size})"
        findViewById<Chip>(R.id.chipExpired).text = "Expirés ($expired)"
        findViewById<Chip>(R.id.chipSoon).text = "Bientôt ($soon)"
        findViewById<Chip>(R.id.chipValid).text = "Valides ($valid)"
    }
}
