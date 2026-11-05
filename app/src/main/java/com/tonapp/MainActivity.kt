package com.tonapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapp.data.AppDatabase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: VehicleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAdd = findViewById<Button>(R.id.btnAddVehicle)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewVehicles)
        val etSearch = findViewById<EditText>(R.id.etSearch)

        adapter = VehicleAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnAdd.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivity::class.java))
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
            }
        }
    }
}
