package com.tonapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    // Récupérer la liste de tous les véhicules (triés par échéance de contrôle technique pour prioriser)
    @Query("SELECT * FROM vehicles ORDER BY technicalControlExpiry ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    // Insérer ou mettre à jour un véhicule et ses informations propriétaire/PDF
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)

    // Supprimer un véhicule
    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity)
}
