package com.tonapp

view class VehicleAdapter(private val vehicles: List<com.tonapp.data.VehicleEntity>) : 
    androidx.recyclerview.widget.RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    class VehicleViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val tvName: android.widget.TextView = view.findViewById(R.id.tvVehicleName)
        val tvPlate: android.widget.TextView = view.findViewById(R.id.tvLicensePlate)
        val tvOwner: android.widget.TextView = view.findViewById(R.id.tvOwnerInfo)
        val tvStatus: android.widget.TextView = view.findViewById(R.id.tvTechnicalControlStatus)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VehicleViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
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
                holder.tvStatus.setTextColor(android.graphics.Color.RED)
            }
            1 -> {
                holder.tvStatus.text = "⚠️ Contrôle Technique Bientôt Expiré (< 30j)"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#FFA000"))
            }
            else -> {
                holder.tvStatus.text = "✅ Contrôle Technique Valide"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#388E3C"))
            }
        }
    }

    override fun getItemCount() = vehicles.size
    }
    
