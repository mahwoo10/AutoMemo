package com.tonapp

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import com.tonapp.utils.HistoryPrefs
import com.tonapp.utils.NotificationScheduler
import com.tonapp.utils.ReminderPrefs
import com.tonapp.utils.StatusFilter
import com.tonapp.utils.VehicleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// sortByPlate = false : tri chronologique par échéance (accueil)
// sortByPlate = true  : tri alphabétique par immatriculation (liste complète)
class VehicleAdapter(
    private var fullList: List<VehicleEntity>,
    private val sortByPlate: Boolean = false
) : RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    private var displayedList: List<VehicleEntity> = emptyList()
    private var query: String = ""
    private var statusFilter: StatusFilter = StatusFilter.ALL
    private var limit: Int? = null

    // Nombre de véhicules correspondant aux filtres, avant application de la limite
    var matchCount: Int = 0
        private set

    private class StatusStyle(val textRes: Int, val bgRes: Int, val stripRes: Int)

    init {
        updateData(fullList)
    }

    fun updateData(newList: List<VehicleEntity>) {
        fullList = if (sortByPlate) {
            newList.sortedBy { it.licensePlate.lowercase(Locale.getDefault()) }
        } else {
            newList.sortedBy { it.technicalControlExpiry }
        }
        applyFilters()
    }

    fun setFilters(status: StatusFilter, newQuery: String, newLimit: Int? = null) {
        statusFilter = status
        query = newQuery.trim()
        limit = newLimit
        applyFilters()
    }

    // Réévalue les statuts (ex. au retour dans l'appli, le lendemain)
    fun refresh() = applyFilters()

    private fun matchesStatus(expiry: Long): Boolean {
        val status = VehicleStatus.of(expiry)
        return when (statusFilter) {
            StatusFilter.ALL -> true
            StatusFilter.UPCOMING -> status != StatusFilter.EXPIRED
            else -> status == statusFilter
        }
    }

    private fun applyFilters() {
        val matches = fullList.filter { v ->
            val matchesQuery = query.isEmpty() ||
                v.name.contains(query, ignoreCase = true) ||
                v.licensePlate.contains(query, ignoreCase = true) ||
                v.ownerFullName.contains(query, ignoreCase = true)
            matchesStatus(v.technicalControlExpiry) && matchesQuery
        }
        matchCount = matches.size
        displayedList = limit?.let { matches.take(it) } ?: matches
        notifyDataSetChanged()
    }

    class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPlate: TextView = view.findViewById(R.id.tvLicensePlate)
        val tvOwner: TextView = view.findViewById(R.id.tvOwnerInfo)
        val tvStatus: TextView = view.findViewById(R.id.tvTechnicalControlStatus)
        val tvExpiryDate: TextView = view.findViewById(R.id.tvExpiryDate)
        val viewStrip: View = view.findViewById(R.id.viewStatusStrip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle, parent, false)
        return VehicleViewHolder(view)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        val vehicle = displayedList[position]
        val ctx = holder.itemView.context
        val expiry = vehicle.technicalControlExpiry

        holder.tvPlate.text = vehicle.licensePlate

        if (vehicle.ownerFullName.isBlank()) {
            holder.tvOwner.visibility = View.GONE
        } else {
            holder.tvOwner.visibility = View.VISIBLE
            holder.tvOwner.text = vehicle.ownerFullName
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        holder.tvExpiryDate.text = "Échéance : ${dateFormat.format(Date(expiry))}"

        // Vert : éloignée | Orange : proche ou aujourd'hui | Rouge : dépassée
        val style = when (VehicleStatus.of(expiry)) {
            StatusFilter.EXPIRED ->
                StatusStyle(R.color.status_red, R.color.status_red_bg, R.color.status_red)
            StatusFilter.TODAY, StatusFilter.SOON ->
                StatusStyle(R.color.status_orange, R.color.status_orange_bg, R.color.status_orange_bar)
            else ->
                StatusStyle(R.color.status_green, R.color.status_green_bg, R.color.status_green)
        }
        holder.tvStatus.text = VehicleStatus.remainingLabel(expiry)
        holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, style.textRes))
        holder.tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, style.bgRes))
        holder.viewStrip.setBackgroundColor(ContextCompat.getColor(ctx, style.stripRes))

        holder.itemView.setOnClickListener {
            val intent = Intent(ctx, VehicleDetailActivity::class.java).apply {
                putExtra("ID", vehicle.id)
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
                        ReminderPrefs.clear(ctx, vehicle.id)
                        HistoryPrefs.clear(ctx, vehicle.id)
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
            true
        }
    }

    override fun getItemCount() = displayedList.size
}
