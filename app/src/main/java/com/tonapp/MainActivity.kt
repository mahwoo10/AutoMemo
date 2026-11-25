package com.tonapp

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import com.tonapp.data.AppDatabase
import com.tonapp.data.VehicleEntity
import com.tonapp.utils.NotificationScheduler
import com.tonapp.utils.SettingsPrefs
import com.tonapp.utils.StatusFilter
import com.tonapp.utils.VehicleStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private companion object {
        const val STATE_PAGE = "STATE_PAGE"
        const val HOME_LIMIT = 5
    }

    private lateinit var homeAdapter: VehicleAdapter
    private lateinit var allAdapter: VehicleAdapter

    private lateinit var pageHome: View
    private lateinit var pageVehicles: View
    private lateinit var pageCalendar: View
    private lateinit var pageSettings: View
    private lateinit var calAdapter: VehicleAdapter
    private val calMonth: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    private var selectedDay: Int? = null
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
        pageCalendar = findViewById(R.id.pageCalendar)
        pageSettings = findViewById(R.id.pageSettings)
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

        calAdapter = VehicleAdapter(emptyList())
        findViewById<RecyclerView>(R.id.recyclerViewCalendar).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = calAdapter
        }
        findViewById<View>(R.id.ivCalPrev).setOnClickListener { changeMonth(-1) }
        findViewById<View>(R.id.ivCalNext).setOnClickListener { changeMonth(1) }
        setupSettings()

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
            R.id.nav_vehicles -> pageVehicles
            R.id.nav_calendar -> pageCalendar
            R.id.nav_settings -> pageSettings
            else -> pageHome
        }
        listOf(pageHome, pageVehicles, pageCalendar, pageSettings).forEach {
            it.visibility = if (it === target) View.VISIBLE else View.GONE
        }

        // Le bouton d'ajout reste visible sur l'accueil et la liste des véhicules
        btnAdd.visibility = if (target === pageHome || target === pageVehicles) View.VISIBLE else View.GONE

        // Transition discrète
        target.alpha = 0f
        target.animate().alpha(1f).setDuration(180).start()
    }

    // ---------- Affichage ----------

    private fun render() {
        updateStats()
        applyHome()
        applyVehicles()
        renderCalendar()
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

    // ---------- Calendrier ----------

    private fun changeMonth(delta: Int) {
        calMonth.add(Calendar.MONTH, delta)
        selectedDay = null
        renderCalendar()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderCalendar() {
        val locale = Locale.getDefault()
        val monthTitle = SimpleDateFormat("LLLL yyyy", locale).format(calMonth.time)
            .replaceFirstChar { it.uppercase() }
        findViewById<TextView>(R.id.tvCalMonth).text = monthTitle

        val year = calMonth.get(Calendar.YEAR)
        val month = calMonth.get(Calendar.MONTH)

        // Échéances du mois, regroupées par jour
        val byDay = HashMap<Int, MutableList<VehicleEntity>>()
        val cal = Calendar.getInstance()
        for (v in vehicles) {
            cal.timeInMillis = v.technicalControlExpiry
            if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                byDay.getOrPut(cal.get(Calendar.DAY_OF_MONTH)) { mutableListOf() }.add(v)
            }
        }

        val today = Calendar.getInstance()
        val isThisMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month
        val daysInMonth = calMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        // Semaine commençant le lundi
        val offset = (calMonth.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val rows = (offset + daysInMonth + 6) / 7

        val grid = findViewById<LinearLayout>(R.id.calendarGrid)
        grid.removeAllViews()
        val dot = dp(36)

        for (r in 0 until rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 7) {
                val day = r * 7 + c - offset + 1
                val cell = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                }
                if (day in 1..daysInMonth) {
                    val tv = TextView(this).apply {
                        layoutParams = FrameLayout.LayoutParams(dot, dot, Gravity.CENTER)
                        gravity = Gravity.CENTER
                        textSize = 14f
                        text = day.toString()
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dark))
                    }
                    val dayVehicles = byDay[day]
                    if (dayVehicles != null) {
                        // Couleur du jour : la plus urgente parmi ses échéances
                        val colorRes = when {
                            dayVehicles.any { VehicleStatus.of(it.technicalControlExpiry) == StatusFilter.EXPIRED } ->
                                R.color.status_red
                            dayVehicles.any { VehicleStatus.of(it.technicalControlExpiry) != StatusFilter.VALID } ->
                                R.color.status_orange_bar
                            else -> R.color.status_green
                        }
                        tv.setBackgroundResource(R.drawable.bg_cal_solid)
                        tv.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
                        tv.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
                    } else if (isThisMonth && day == today.get(Calendar.DAY_OF_MONTH)) {
                        tv.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
                        tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
                    }
                    if (selectedDay == day) {
                        tv.foreground = ContextCompat.getDrawable(this, R.drawable.bg_cal_ring)
                    }
                    cell.setOnClickListener {
                        selectedDay = if (selectedDay == day) null else day
                        renderCalendar()
                    }
                    cell.addView(tv)
                }
                row.addView(cell)
            }
            grid.addView(row)
        }

        // Liste : le jour choisi, sinon tout le mois
        val from = Calendar.getInstance().apply {
            clear()
            set(year, month, selectedDay ?: 1, 0, 0, 0)
        }
        val to = Calendar.getInstance().apply {
            clear()
            set(year, month, selectedDay ?: daysInMonth, 23, 59, 59)
        }
        calAdapter.updateData(vehicles)
        calAdapter.setDateRange(from.timeInMillis, to.timeInMillis)

        val day = selectedDay
        findViewById<TextView>(R.id.tvCalListTitle).text = if (day != null) {
            "Échéances du " + SimpleDateFormat("d MMMM", locale).format(from.time)
        } else {
            "Échéances du mois (${calAdapter.itemCount})"
        }
        findViewById<View>(R.id.tvCalEmpty).visibility =
            if (calAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    // ---------- Paramètres ----------

    private fun setupSettings() {
        val defs = listOf(
            30 to R.id.switchDay30,
            15 to R.id.switchDay15,
            7 to R.id.switchDay7,
            3 to R.id.switchDay3,
            0 to R.id.switchDay0
        )
        for ((days, id) in defs) {
            val sw = findViewById<SwitchCompat>(id)
            sw.isChecked = SettingsPrefs.isDayEnabled(this, days)
            sw.setOnCheckedChangeListener { _, checked ->
                SettingsPrefs.setDayEnabled(this, days, checked)
                rescheduleAll()
            }
        }

        val btnTime = findViewById<Button>(R.id.btnReminderTime)
        fun showTime() {
            btnTime.text = String.format(
                Locale.getDefault(), "%02d:%02d", SettingsPrefs.hour(this), SettingsPrefs.minute(this)
            )
        }
        showTime()
        btnTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    SettingsPrefs.setTime(this, hour, minute)
                    showTime()
                    rescheduleAll()
                },
                SettingsPrefs.hour(this),
                SettingsPrefs.minute(this),
                true
            ).show()
        }

        val labels = arrayOf("Système", "Clair", "Sombre")
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val tvTheme = findViewById<TextView>(R.id.tvThemeValue)
        val current = modes.indexOf(SettingsPrefs.themeMode(this)).coerceAtLeast(0)
        tvTheme.text = labels[current]
        findViewById<View>(R.id.rowTheme).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Thème")
                .setSingleChoiceItems(labels, modes.indexOf(SettingsPrefs.themeMode(this)).coerceAtLeast(0)) { dialog, which ->
                    dialog.dismiss()
                    SettingsPrefs.setThemeMode(this, modes[which])
                    // L'activité est recréée avec le nouveau thème (la rubrique courante est conservée)
                    AppCompatDelegate.setDefaultNightMode(modes[which])
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    // Les délais ou l'heure ont changé : tous les rappels sont reprogrammés
    private fun rescheduleAll() {
        lifecycleScope.launch {
            val all = AppDatabase.getDatabase(this@MainActivity).vehicleDao().getAllOnce()
            NotificationScheduler.rescheduleAll(this@MainActivity, all)
            Toast.makeText(this@MainActivity, "Rappels mis à jour", Toast.LENGTH_SHORT).show()
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
