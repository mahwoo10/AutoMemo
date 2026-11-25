package com.tonapp.utils

import android.content.Context
import android.net.Uri
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Sauvegarde / restauration des véhicules (et de leur historique) dans un fichier JSON.
object BackupManager {

    private const val APP = "AutoMemo"

    // Écrit tous les véhicules dans le fichier choisi ; renvoie leur nombre
    suspend fun export(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val vehicles = AppDatabase.getDatabase(context).vehicleDao().getAllOnce()
        val array = JSONArray()
        for (v in vehicles) {
            val o = JSONObject()
            o.put("id", v.id)
            o.put("name", v.name)
            o.put("licensePlate", v.licensePlate)
            o.put("ownerFullName", v.ownerFullName)
            o.put("ownerPhoneNumber", v.ownerPhoneNumber)
            o.put("ownerEmail", v.ownerEmail)
            o.put("ownerAddress", v.ownerAddress)
            o.put("technicalControlExpiry", v.technicalControlExpiry)
            o.put("technicalControlPdfPath", v.technicalControlPdfPath ?: JSONObject.NULL)
            o.put("tvmExpiry", v.tvmExpiry ?: JSONObject.NULL)
            o.put("tvmReceiptPdfPath", v.tvmReceiptPdfPath ?: JSONObject.NULL)
            val history = JSONArray()
            HistoryPrefs.get(context, v.id).forEach { history.put(it) }
            o.put("history", history)
            array.put(o)
        }
        val root = JSONObject()
        root.put("app", APP)
        root.put("version", 1)
        root.put("vehicles", array)

        val out = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Fichier inaccessible")
        out.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
        vehicles.size
    }

    // Ajoute ou met à jour les véhicules du fichier, puis reprogramme les rappels
    suspend fun import(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("Fichier inaccessible")

        val root = JSONObject(text)
        if (root.optString("app") != APP) throw IllegalArgumentException("Ce fichier n'est pas une sauvegarde AutoMemo")
        val array = root.getJSONArray("vehicles")

        // On lit tout avant d'écrire, pour ne rien modifier si le fichier est invalide
        val parsed = ArrayList<Pair<VehicleEntity, List<Long>>>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val vehicle = VehicleEntity(
                id = o.getString("id"),
                name = o.optString("name"),
                licensePlate = o.optString("licensePlate"),
                ownerFullName = o.optString("ownerFullName"),
                ownerPhoneNumber = o.optString("ownerPhoneNumber"),
                ownerEmail = o.optString("ownerEmail"),
                ownerAddress = o.optString("ownerAddress"),
                technicalControlExpiry = o.getLong("technicalControlExpiry"),
                technicalControlPdfPath = if (o.isNull("technicalControlPdfPath")) null else o.getString("technicalControlPdfPath"),
                tvmExpiry = if (o.isNull("tvmExpiry")) null else o.getLong("tvmExpiry"),
                tvmReceiptPdfPath = if (o.isNull("tvmReceiptPdfPath")) null else o.getString("tvmReceiptPdfPath")
            )
            val history = ArrayList<Long>()
            o.optJSONArray("history")?.let { h -> for (j in 0 until h.length()) history.add(h.getLong(j)) }
            parsed.add(vehicle to history)
        }

        val dao = AppDatabase.getDatabase(context).vehicleDao()
        for ((vehicle, history) in parsed) {
            dao.insertVehicle(vehicle)
            HistoryPrefs.setAll(context, vehicle.id, history)
        }
        NotificationScheduler.rescheduleAll(context, parsed.map { it.first })
        parsed.size
    }
}
