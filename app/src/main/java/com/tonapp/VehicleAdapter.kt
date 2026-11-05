package com.tonapp

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tonapp.data.VehicleEntity

class VehicleAdapter(private val vehicles: List<VehicleEntity>) : 
    RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    class VehicleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvVehicleName)
        val tvPlate: TextView = view.findViewById(R.id.tvLicensePlate)
        val tvOwner: TextView = view.findViewById(R.id.tvOwnerInfo)
        val tvStatus: TextView = view.findViewById(R.id.tvTechnicalControlStatus)
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
        holder.tvPlate.text = "Immat: ${vehicle.licensePlate}"
        holder.tvOwner.text = "Propriétaire : ${vehicle.ownerFullName} (${vehicle.ownerPhoneNumber})"
        
        val status = com.tonapp.utils.VehicleHelper.getTechnicalControlStatus(
            com.tonapp.model.Vehicle(
                vehicle.id, vehicle.name, vehicle.licensePlate,
                com.tonapp.model.Owner(vehicle.ownerFullName, vehicle.ownerPhoneNumber, vehicle.ownerEmail, vehicle.ownerAddress),
                vehicle.technicalControlExpiry, vehicle.technicalControlPdfPath, vehicle.tvmExpiry, vehicle.tvmReceiptPdfPath
            )
        )

        when (status) {
            2 -> {
                holder.tvStatus.text = "⚠️ Contrôle Technique EXPIRÉ !"
                holder.tvStatus.setTextColor(Color.RED)
            }
            1 -> {
                holder.tvStatus.text = "⚠️ Contrôle Technique Bientôt Expiré (< 30j)"
                holder.tvStatus.setTextColor(Color.parseColor("#FFA000"))
            }
            else -> {
                holder.tvStatus.text = "✅ Contrôle Technique Valide"
                holder.tvStatus.setTextColor(Color.parseColor("#388E3C"))
            }
        }

        // Gestion de l'affichage et du clic sur le PDF du contrôle technique
        if (!vehicle.technicalControlPdfPath.isNullOrEmpty()) {
            holder.tvPdfAction.visibility = View.VISIBLE
            holder.tvPdfAction.text = "📄 Afficher le certificat PDF"
            holder.tvPdfAction.setOnClickListener {
                val context = holder.itemView.context
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(vehicle.technicalControlPdfPath), "application/pdf")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Si aucun lecteur PDF n'est installé
                }
            }
        } else {
            holder.tvPdfAction.visibility = View.GONE
        }
    }

    override fun getItemCount() = vehicles.size
    }
    
