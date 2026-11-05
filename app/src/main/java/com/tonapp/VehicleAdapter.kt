package com.tonapp

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import com.tonapp.utils.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VehicleAdapter(private var fullList: List<VehicleEntity>) :
    RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    private var displayedList: List<VehicleEntity> = emptyList()

    private class StatusStyle(val label: String, val textRes: Int, val bgRes: Int, val barRes: Int)

    init {
        updateData(fullList)
    }

    fun updateData(newList: List<VehicleEntity>) {
        fullList = newList.sortedBy { it.technicalControlExpiry }
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
        displayedList = filtered.sortedBy { it.technicalControlExpiry }
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
        val ctx = holder.itemView.context

        holder.tvName.text = vehicle.name
        holder.tvPlate.text = vehicle.licensePlate

        val ownerParts = listOf(vehicle.ownerFullName, vehicle.ownerPhoneNumber).filter { it.isNotBlank() }
        holder.tvOwner.text = if (ownerParts.isEmpty()) {
            "Propriétaire non renseigné"
        } else {
            "Propriétaire : " + ownerParts.joinToString(" · ")
        }

        val expiry = vehicle.technicalControlExpiry
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        holder.tvExpiryDate.text = "Échéance : ${dateFormat.format(Date(expiry))}"

        val daysRemaining = ((expiry - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()

        val progress = when {
            daysRemaining <= 0 -> 100
            daysRemaining >= 365 -> 100
            else -> ((daysRemaining.toFloat() / 365f) * 100).toInt()
        }
        holder.progressExpiry.progress = progress

        val style = when {
            daysRemaining <= 0 -> StatusStyle(
                "Expiré", R.color.status_red, R.color.status_red_bg, R.color.status_red
            )
            daysRemaining <= 30 -> StatusStyle(
                "Expire dans $daysRemaining jour(s)", R.color.status_orange, R.color.status_orange_bg, R.color.status_orange_bar
            )
            else -> StatusStyle(
                "Valide ($daysRemaining j)", R.color.status_green, R.color.status_green_bg, R.color.status_green
            )
        }
        holder.tvStatus.text = style.label
        holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, style.textRes))
        holder.tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, style.bgRes))
        holder.progressExpiry.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, style.barRes))

        if (!vehicle.technicalControlPdfPath.isNullOrEmpty()) {
            holder.tvPdfAction.visibility = View.VISIBLE
            val uri = Uri.parse(vehicle.technicalControlPdfPath)
            val mimeType = getMimeType(ctx, uri)
            val isPdf = mimeType.contains("pdf", ignoreCase = true)

            holder.tvPdfAction.text = if (isPdf) "📄 Voir le PDF" else "🖼️ Voir la photo"

            holder.tvPdfAction.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                try {
                    ctx.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Aucune application disponible pour ouvrir ce fichier", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            holder.tvPdfAction.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(ctx, VehicleDetailActivity::class.java).apply {
                putExtra("NAME", vehicle.name)
                putExtra("PLATE", vehicle.licensePlate)
                putExtra("EXPIRY", vehicle.technicalControlExpiry)
                putExtra("DOC_PATH", vehicle.technicalControlPdfPath)
            }
            ctx.startActivity(intent)
        }

        holder.itemView.setOnLongClickListener {
            AlertDialog.Builder(ctx)
                .setTitle("Supprimer le véhicule")
                .setMessage("Voulez-vous vraiment supprimer ${vehicle.name} (${vehicle.licensePlate}) ?")
                .setPositiveButton("Supprimer") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        AppDatabase.getDatabase(ctx).vehicleDao().deleteVehicle(vehicle)
                        NotificationScheduler.cancelReminders(ctx, vehicle.id)
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
            true
        }
    }

    override fun getItemCount() = displayedList.size
}
