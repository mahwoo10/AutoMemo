package com.tonapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import com.tonapp.utils.StatusFilter
import com.tonapp.utils.VehicleStatus
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private companion object {
        const val STATE_PAGE = "STATE_PAGE"
        const val HOME_LIMIT = 5
    }

    private lateinit var homeAdapter: VehicleAdapter
    private lateinit var allAdapter: VehicleAdapter

    private lateinit var pageHome: View
    private lateinit var pageVehicles: View
    private lateinit var pagePlaceholder: View
    private lateinit var btnAdd: View
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var etSearch: EditText
    private lateinit var etSearchVehicles: EditText
    private lateinit var tvEmptyState: TextView
    private lateinit var tvEmptyVehicles: TextView
    private lateinit var tvSeeAll: TextView

    private var vehicles: List<VehicleEntity> = emptyList()
    private var homeFilter = StatusFilter.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pageHome = findViewById(R.id.pageHome)
        pageVehicles = findViewById(R.id.pageVehicles)
        pagePlaceholder = findViewById(R.id.pagePlaceholder)
        btnAdd = findViewById(R.id.btnAddVehicle)
        bottomNav = findViewById(R.id.bottomNav)
        etSearch = findViewById(R.id.etSearch)
        etSearchVehicles = findViewById(R.id.etSearchVehicles)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvEmptyVehicles = findViewById(R.id.tvEmptyVehicles)
        tvSeeAll = findViewById(R.id.tvSeeAll)
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupStatus)

        // Accueil : échéances triées par date | Véhicules : liste complète par immatriculation
        homeAdapter = VehicleAdapter(emptyList())
        findViewById<RecyclerView>(R.id.recyclerViewVehicles).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = homeAdapter
        }
        allAdapter = VehicleAdapter(emptyList(), sortByPlate = true)
        findViewById<RecyclerView>(R.id.recyclerViewAllVehicles).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = allAdapter
        }

        btnAdd.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivity::class.java))
        }

        findViewById<View>(R.id.btnNotifications).setOnClickListener { openNotifications() }
        tvSeeAll.setOnClickListener { bottomNav.selectedItemId = R.id.nav_vehicles }

        // Filtres : Tous / À venir / Expirés
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            homeFilter = when (checkedIds.firstOrNull()) {
                R.id.chipExpired -> StatusFilter.EXPIRED
                R.id.chipUpcoming -> StatusFilter.UPCOMING
                else -> StatusFilter.ALL
            }
            applyHome()
        }

        // Toucher un indicateur applique le filtre correspondant
        findViewById<View>(R.id.statTotal).setOnClickListener { chipGroup.check(R.id.chipAll) }
        findViewById<View>(R.id.statSoon).setOnClickListener { chipGroup.check(R.id.chipUpcoming) }
        findViewById<View>(R.id.statToday).setOnClickListener { chipGroup.check(R.id.chipUpcoming) }
        findViewById<View>(R.id.statExpired).setOnClickListener { chipGroup.check(R.id.chipExpired) }

        etSearch.addTextChangedListener(onTextChanged { applyHome() })
        etSearchVehicles.addTextChangedListener(onTextChanged { applyVehicles() })

        // Barre de navigation inférieure
        bottomNav.setOnItemSelectedListener { item ->
            showPage(item.itemId)
            true
        }
        bottomNav.selectedItemId = savedInstanceState?.getInt(STATE_PAGE, R.id.nav_home) ?: R.id.nav_home

        // Retour : depuis une autre rubrique on revient d'abord à l'accueil
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (bottomNav.selectedItemId != R.id.nav_home) {
                    bottomNav.selectedItemId = R.id.nav_home
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        lifecycleScope.launch {
            AppDatabase.getDatabase(this@MainActivity).vehicleDao().getAllVehicles().collect { list ->
                vehicles = list
                render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Les statuts dépendent de la date du jour : on les recalcule au retour dans l'appli
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, bottomNav.selectedItemId)
    }

    private fun onTextChanged(action: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action()
        override fun afterTextChanged(s: Editable?) {}
    }

    // ---------- Navigation ----------

    private fun showPage(itemId: Int) {
        val target: View = when (itemId) {
            R.id.nav_home -> pageHome
            R.id.nav_vehicles -> pageVehicles
            else -> pagePlaceholder
        }
        pageHome.visibility = if (target === pageHome) View.VISIBLE else View.GONE
        pageVehicles.visibility = if (target === pageVehicles) View.VISIBLE else View.GONE
        pagePlaceholder.visibility = if (target === pagePlaceholder) View.VISIBLE else View.GONE

        if (target === pagePlaceholder) {
            val calendar = itemId == R.id.nav_calendar
            findViewById<TextView>(R.id.tvPlaceholderTitle).text = if (calendar) "Calendrier" else "Paramètres"
            findViewById<ImageView>(R.id.ivPlaceholder).setImageResource(
                if (calendar) R.drawable.ic_calendar else R.drawable.ic_settings
            )
        }

        // Le bouton d'ajout reste visible sur l'accueil et la liste des véhicules
        btnAdd.visibility = if (target === pagePlaceholder) View.GONE else View.VISIBLE

        // Transition discrète
        target.alpha = 0f
        target.animate().alpha(1f).setDuration(180).start()
    }

    // ---------- Affichage ----------

    private fun render() {
        updateStats()
        applyHome()
        applyVehicles()
    }

    private fun applyHome() {
        val query = etSearch.text.toString()
        // Sans filtre ni recherche, l'accueil n'affiche que les prochaines échéances
        val limit = if (homeFilter == StatusFilter.ALL && query.isBlank()) HOME_LIMIT else null
        homeAdapter.updateData(vehicles)
        homeAdapter.setFilters(homeFilter, query, limit)

        tvSeeAll.visibility = if (homeAdapter.matchCount > homeAdapter.itemCount) View.VISIBLE else View.GONE

        if (homeAdapter.itemCount == 0) {
            tvEmptyState.text = if (vehicles.isEmpty()) {
                "Aucun véhicule pour l'instant\nAppuyez sur « Ajouter » pour commencer"
            } else {
                "Aucun véhicule dans cette liste"
            }
            tvEmptyState.visibility = View.VISIBLE
        } else {
            tvEmptyState.visibility = View.GONE
        }
    }

    private fun applyVehicles() {
        allAdapter.updateData(vehicles)
        allAdapter.setFilters(StatusFilter.ALL, etSearchVehicles.text.toString())

        val n = vehicles.size
        findViewById<TextView>(R.id.tvVehiclesCount).text =
            if (n <= 1) "$n véhicule enregistré" else "$n véhicules enregistrés"

        if (allAdapter.itemCount == 0) {
            tvEmptyVehicles.text = if (vehicles.isEmpty()) {
                "Aucun véhicule pour l'instant"
            } else {
                "Aucun résultat"
            }
            tvEmptyVehicles.visibility = View.VISIBLE
        } else {
            tvEmptyVehicles.visibility = View.GONE
        }
    }

    private fun updateStats() {
        var soon = 0
        var today = 0
        var expired = 0
        for (v in vehicles) {
            when (VehicleStatus.of(v.technicalControlExpiry)) {
                StatusFilter.EXPIRED -> expired++
                StatusFilter.TODAY -> today++
                StatusFilter.SOON -> soon++
                else -> {}
            }
        }
        findViewById<TextView>(R.id.tvCountTotal).text = vehicles.size.toString()
        findViewById<TextView>(R.id.tvCountSoon).text = soon.toString()
        findViewById<TextView>(R.id.tvCountToday).text = today.toString()
        findViewById<TextView>(R.id.tvCountExpired).text = expired.toString()

        // Pastille sur la cloche s'il y a une échéance du jour ou dépassée
        findViewById<View>(R.id.viewBellDot).visibility =
            if (today + expired > 0) View.VISIBLE else View.GONE

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        findViewById<TextView>(R.id.tvWelcome).text = if (hour in 5..17) "Bonjour 👋" else "Bonsoir 👋"
        findViewById<TextView>(R.id.tvWelcomeSub).text = when {
            vehicles.isEmpty() -> "Ajoutez votre premier véhicule"
            expired > 0 -> "$expired échéance(s) dépassée(s) à régulariser"
            today > 0 -> "$today échéance(s) aujourd'hui"
            soon > 0 -> "$soon échéance(s) à surveiller"
            else -> "Toutes vos échéances sont à jour"
        }
    }

    // ---------- Notifications ----------

    private fun openNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            return
        }
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Aucun écran de réglages disponible : rien à faire
        }
    }
}
