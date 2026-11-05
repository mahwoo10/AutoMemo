package com.tonapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY technicalControlExpiry ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    // Lecture unique (utilisée après un redémarrage pour reprogrammer les rappels)
    @Query("SELECT * FROM vehicles")
    suspend fun getAllOnce(): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)

    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity)
}
