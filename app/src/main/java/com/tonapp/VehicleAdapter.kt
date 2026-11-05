package com.tonapp

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VehicleAdapter(private val vehicles: List<VehicleEntity>) :
    RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvVehicleName)
        val tvPlate: TextView = view.findViewById(R.id.tvLicensePlate)
        val tvOwner: TextView = view.findViewById(R.id.tvOwnerInfo)
        val tvStatus: TextView = view.findViewById(R.id.tvTechnicalControlStatus)
        val tvExpiryDate: TextView = view.findViewById(R.id.tvExpiryDate)
        val progressExpiry: ProgressBar = view.findViewById(R.id.progressExpiry)
        val tvPdfAction: TextView = view.findViewById(R.id.tvPdfAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle, parent, false)
        return VehicleViewHolder(view)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        val vehicle = vehicles[position]
        holder.tvName.text = vehicle.name
        holder.tvPlate.text = "- ${vehicle.licensePlate}"
        holder.tvOwner.text = "Propriétaire : ${vehicle.ownerFullName} (${vehicle.ownerPhoneNumber})"

        val expiry = vehicle.technicalControlExpiry ?: System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        holder.tvExpiryDate.text = dateFormat.format(Date(expiry))

        // Calcul du temps restant
        val currentTime = System.currentTimeMillis()
        val diffMillis = expiry - currentTime
        val daysRemaining = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        val progress = when {
            daysRemaining <= 0 -> 100
            daysRemaining >= 365 -> 100
            else -> ((daysRemaining.toFloat() / 365f) * 100).toInt()
        }
        holder.progressExpiry.progress = progress

        // Adaptation de la couleur de la jauge et du texte selon l'échéance
        when {
            daysRemaining <= 0 -> {
                holder.tvStatus.text = "❌ Contrôle Technique EXPIRÉ !"
                holder.tvStatus.setTextColor(Color.RED)
                holder.progressExpiry.progressTintList = ColorStateList.valueOf(Color.RED)
            }
            daysRemaining <= 30 -> {
                val statusColor = Color.parseColor("#FFA000")
                holder.tvStatus.text = "⚠️ Expire dans $daysRemaining jour(s)"
                holder.tvStatus.setTextColor(statusColor)
                holder.progressExpiry.progressTintList = ColorStateList.valueOf(statusColor)
            }
            else -> {
                val statusColor = Color.parseColor("#2E7D32")
                holder.tvStatus.text = "✅ Contrôle Technique Valide ($daysRemaining j)"
                holder.tvStatus.setTextColor(statusColor)
                holder.progressExpiry.progressTintList = ColorStateList.valueOf(statusColor)
            }
        }

        // Action sur le document attaché
        if (!vehicle.technicalControlPdfPath.isNullOrEmpty()) {
            holder.tvPdfAction.visibility = View.VISIBLE
            val isPdf = vehicle.technicalControlPdfPath.endsWith(".pdf", ignoreCase = true)
            holder.tvPdfAction.text = if (isPdf) "📄 Voir le PDF" else "🖼️ Voir la Photo"

            holder.tvPdfAction.setOnClickListener {
                val context = holder.itemView.context
                val uri = Uri.parse(vehicle.technicalControlPdfPath)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val mimeType = if (isPdf) "application/pdf" else "image/*"
                    setDataAndType(uri, mimeType)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Aucune application disponible pour ouvrir ce fichier", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            holder.tvPdfAction.visibility = View.GONE
        }

        // Appui long pour supprimer le véhicule
        holder.itemView.setOnLongClickListener {
            val context = holder.itemView.context
            AlertDialog.Builder(context)
                .setTitle("Supprimer le véhicule")
                .setMessage("Voulez-vous vraiment supprimer ${vehicle.name} (${vehicle.licensePlate}) ?")
                .setPositiveButton("Supprimer") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        AppDatabase.getDatabase(context).vehicleDao().deleteVehicle(vehicle)
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
            true
        }
    }

    override fun getItemCount() = vehicles.size
}
