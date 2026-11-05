package com.tonapp.utils

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher

object PdfManagerHelper {

    // Lance l'sélecteur de documents pour récupérer un PDF (ex: certificat du contrôle technique)
    fun openPdfSelector(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        launcher.launch(intent)
    }
}
