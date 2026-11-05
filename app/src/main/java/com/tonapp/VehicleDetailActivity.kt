package com.tonapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VehicleDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_detail)

        val name = intent.getStringExtra("NAME") ?: "Véhicule"
        val plate = intent.getStringExtra("PLATE") ?: ""
        val expiry = intent.getLongExtra("EXPIRY", System.currentTimeMillis())
        val docPath = intent.getStringExtra("DOC_PATH")

        findViewById<TextView>(R.id.tvDetailTitle).text = name
        findViewById<TextView>(R.id.tvDetailPlate).text = plate

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        findViewById<TextView>(R.id.tvDetailExpiry).text = "Expires le : ${dateFormat.format(Date(expiry))}"

        val btnViewDoc = findViewById<Button>(R.id.btnViewDoc)
        if (!docPath.isNullOrEmpty()) {
            btnViewDoc.setOnClickListener {
                val uri = Uri.parse(docPath)
                val isPdf = docPath.endsWith(".pdf", ignoreCase = true)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, if (isPdf) "application/pdf" else "image/*")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Impossible d'ouvrir le justificatif", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnViewDoc.visibility = View.GONE
        }
    }
}
