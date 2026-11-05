package com.tonapp

import android.app.AlertDialog
import android.content.Context
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

class VehicleAdapter(private var fullList: List<VehicleEntity>) :
    RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    private var displayedList: List<VehicleEntity> = emptyList()

    init {
        updateData(fullList)
    }

    fun updateData(newList: List<VehicleEntity>) {
        fullList = newList.sortedBy { it.technicalControlExpiry ?: Long.MAX_VALUE }
        displayedList = fullList
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val filtered = if (query.isEmpty()) {
            fullList
        } else {
            fullList.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.licensePlate.contains(query, ignoreCase = true) ||
                it.ownerFullName.contains(query, ignoreCase = true)
            }
        }
        displayedList = filtered.sortedBy { it.technicalControlExpiry ?: Long.MAX_VALUE }
        notifyDataSetChanged()
    }

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

    private fun getMimeType(context: Context, uri: Uri): String {
        val typeFromResolver = try {
            context.contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }
        return typeFromResolver ?: if (uri.toString().contains(".pdf", ignoreCase = true)) "application/pdf" else "image/*"
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        val vehicle = displayedList[position]
        holder.tvName.text = vehicle.name
        holder.tvPlate.text = "- ${vehicle.licensePlate}"
        holder.tvOwner.text = "Propriétaire : ${vehicle.ownerFullName} (${vehicle.ownerPhoneNumber})"

        val expiry = vehicle.technicalControlExpiry ?: System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        holder.tvExpiryDate.text = dateFormat.format(Date(expiry))

        val currentTime = System.currentTimeMillis()
        val diffMillis = expiry - currentTime
        val daysRemaining = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        val progress = when {
            daysRemaining <= 0 -> 100
            daysRemaining >= 365 -> 100
            else -> ((daysRemaining.toFloat() / 365f) * 100).toInt()
        }
        holder.progressExpiry.progress = progress

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

        if (!vehicle.technicalControlPdfPath.isNullOrEmpty()) {
            holder.tvPdfAction.visibility = View.VISIBLE
            val context = holder.itemView.context
            val uri = Uri.parse(vehicle.technicalControlPdfPath)
            val mimeType = getMimeType(context, uri)
            val isPdf = mimeType.contains("pdf", ignoreCase = true)

            holder.tvPdfAction.text = if (isPdf) "📄 Voir le PDF" else "🖼️ Voir la Photo"

            holder.tvPdfAction.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
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

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, VehicleDetailActivity::class.java).apply {
                putExtra("NAME", vehicle.name)
                putExtra("PLATE", vehicle.licensePlate)
                putExtra("EXPIRY", vehicle.technicalControlExpiry)
                putExtra("DOC_PATH", vehicle.technicalControlPdfPath)
            }
            context.startActivity(intent)
        }

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

    override fun getItemCount() = displayedList.size
    }
    
