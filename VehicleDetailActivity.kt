package com.tonapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
        findViewById<TextView>(R.id.tvDetailExpiry).text = "Expire le : ${dateFormat.format(Date(expiry))}"

        val cardGlobalStatus = findViewById<CardView>(R.id.cardGlobalStatus)
        val tvGlobalStatus = findViewById<TextView>(R.id.tvGlobalStatus)
        val daysRemaining = ((expiry - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()

        if (daysRemaining <= 0) {
            tvGlobalStatus.text = "Statut Global : EXPIRÉ !"
            tvGlobalStatus.setTextColor(Color.parseColor("#D32F2F"))
            cardGlobalStatus.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
        } else if (daysRemaining <= 30) {
            tvGlobalStatus.text = "Statut Global : Expire bientôt ($daysRemaining j)"
            tvGlobalStatus.setTextColor(Color.parseColor("#FFA000"))
            cardGlobalStatus.setCardBackgroundColor(Color.parseColor("#FFF8E1"))
        } else {
            tvGlobalStatus.text = "Statut Global : À jour"
            tvGlobalStatus.setTextColor(Color.parseColor("#2E7D32"))
            cardGlobalStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
        }

        val btnViewDoc = findViewById<Button>(R.id.btnViewDoc)
        if (!docPath.isNullOrEmpty()) {
            val uri = Uri.parse(docPath)
            val mimeType = getMimeType(this, uri)
            val isPdf = mimeType.contains("pdf", ignoreCase = true)

            btnViewDoc.text = if (isPdf) "📄 Afficher le PDF" else "🖼️ Afficher la Photo"

            btnViewDoc.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
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

    private fun getMimeType(context: Context, uri: Uri): String {
        val typeFromResolver = try {
            context.contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }
        return typeFromResolver ?: if (uri.toString().contains(".pdf", ignoreCase = true)) "application/pdf" else "image/*"
    }
}
