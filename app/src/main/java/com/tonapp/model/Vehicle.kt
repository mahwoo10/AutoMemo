package com.tonapp.model

data class Owner(
    val fullName: String,
    val phoneNumber: String,
    val email: String,
    val address: String
)

data class Vehicle(
    val id: String,
    val name: String,                    // Ex: Honda CGL 125
    val licensePlate: String,            // Numéro d'immatriculation
    val owner: Owner,                    // Coordonnées du propriétaire
    val technicalControlExpiry: Long,    // Priorité 1 : Expiration du contrôle technique (timestamp)
    val technicalControlPdfPath: String?, // Chemin ou URI vers le PDF du contrôle technique
    val tvmExpiry: Long?,                // Secondaire : Expiration de la TVM
    val tvmReceiptPdfPath: String?       // Chemin ou URI vers le PDF de la TVM
)
