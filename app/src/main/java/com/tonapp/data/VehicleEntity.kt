package com.tonapp.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey
    val id: String,
    val name: String,                    // Ex: Honda CGL 125
    val licensePlate: String,            // Numéro d'immatriculation
    
    // Informations du propriétaire intégrées
    val ownerFullName: String,
    val ownerPhoneNumber: String,
    val ownerEmail: String,
    val ownerAddress: String,
    
    val technicalControlExpiry: Long,    // Priorité 1 : Expiration du contrôle technique
    val technicalControlPdfPath: String?, // Chemin local vers le PDF du contrôle technique
    val tvmExpiry: Long?,                // Secondaire : Expiration de la TVM
    val tvmReceiptPdfPath: String?       // Chemin local vers le PDF de la TVM
)
