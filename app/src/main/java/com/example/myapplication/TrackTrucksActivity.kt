package com.example.myapplication

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.fragments.MapboxFragment
import com.example.myapplication.models.LocationsResponse
import com.example.myapplication.models.TruckLocation
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.GpsStatusMonitor
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TrackTrucksActivity : AppCompatActivity(), MapboxFragment.OnTrucksUpdatedListener {
    private lateinit var sessionManager: SessionManager
    private lateinit var truckListContainer: LinearLayout
    private lateinit var coverageSection: View
    private lateinit var purokChecklistContainer: LinearLayout
    private lateinit var routeProgress: LinearProgressIndicator
    private var selectedTruckId: String? = null
    private var isOptimizationActive = false
    private var isHistoryActive = false
    private var mapFragment: MapboxFragment? = null
    private var isGpsActive: Boolean = true
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L // 5 seconds

    private val updateRunnable = object : Runnable {
        override fun run() {
            // We no longer need to call fetchTruckLocations() manually for the list
            // because MapboxFragment is now pushing Firebase data to us via onTrucksUpdated
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        // Continuous GPS Monitoring
        lifecycle.addObserver(GpsStatusMonitor(this) { isEnabled ->
            isGpsActive = isEnabled
            if (!isEnabled) {
                mapFragment?.clearMap()
            }
        })

        enableEdgeToEdge()
        setContentView(R.layout.activity_track_trucks)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.track_trucks_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        truckListContainer = findViewById(R.id.truck_list_container)
        coverageSection = findViewById(R.id.coverage_section)
        purokChecklistContainer = findViewById(R.id.purok_checklist_container)
        routeProgress = findViewById(R.id.route_progress)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment_container) as? MapboxFragment
        if (mapFragment == null) {
            mapFragment = MapboxFragment.newInstance(MapboxFragment.MODE_FULL)
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment_container, mapFragment!!)
                .commit()
        }
        
        // Connect Firebase data from Fragment to this Activity's list
        mapFragment?.setOnTrucksUpdatedListener(this)

        setupBottomNavigation()
    }

    override fun onTrucksUpdated(trucks: List<TruckLocation>) {
        updateTruckList(trucks)
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onStop() {
        super.onStop()
        mapFragment?.view?.findViewById<com.mapbox.maps.MapView>(R.id.mapView)?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapFragment?.view?.findViewById<com.mapbox.maps.MapView>(R.id.mapView)?.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapFragment?.view?.findViewById<com.mapbox.maps.MapView>(R.id.mapView)?.onLowMemory()
    }

    private fun fetchTruckLocations() {
        RetrofitClient.instance.getLocations().enqueue(object : Callback<LocationsResponse> {
            override fun onResponse(call: Call<LocationsResponse>, response: Response<LocationsResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val trucks = response.body()?.data ?: emptyList()
                    updateTruckList(trucks)
                    mapFragment?.updateTrucks(trucks, autoCenterOnFirst = false)
                }
            }

            override fun onFailure(call: Call<LocationsResponse>, t: Throwable) {
                // Log error
            }
        })
    }

    private fun updateTruckList(trucks: List<TruckLocation>) {
        // Simple check to avoid redundant UI updates if the truck list content hasn't changed
        // This helps resolve the "abnormal implementation" log by reducing layout passes
        truckListContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        trucks.forEach { truck ->
            val card = inflater.inflate(R.layout.item_truck_card, truckListContainer, false)
            val isSelected = truck.truckId == selectedTruckId
            
            if (isSelected) {
                card.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_root)
                    ?.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#2196F3")))
                card.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_root)
                    ?.setStrokeWidth(4)
                
                // Also update coverage if this is the selected truck being refreshed
                updateCoverageUI(truck.truckId)
            }

            card.findViewById<TextView>(R.id.tv_truck_id).text = truck.truckId
            card.findViewById<TextView>(R.id.tv_plate_number).text = truck.plateNumber ?: "N/A"
            card.findViewById<TextView>(R.id.tv_driver_name).text = truck.driverName ?: "Unknown"
            card.findViewById<TextView>(R.id.tv_speed).text = "${truck.speed.toInt()} km/h"
            card.findViewById<TextView>(R.id.tv_last_update).text = "🕒 Last Update: ${truck.updatedAt}"
            
            val statusBadge = card.findViewById<TextView>(R.id.tv_status_badge)
            statusBadge.text = truck.status.uppercase()
            
            // Analytics
            val analytics = mapFragment?.getAnalytics(truck.truckId)
            card.findViewById<TextView>(R.id.tv_distance).text = String.format("%.1f km", analytics?.distanceKm ?: 0.0)
            card.findViewById<TextView>(R.id.tv_fuel).text = String.format("%.1f L", analytics?.fuelLiters ?: 0.0)
            card.findViewById<TextView>(R.id.tv_stops).text = "${analytics?.stops ?: 0}"

            when (truck.status.lowercase()) {
                "active" -> {
                    statusBadge.setTextColor(Color.parseColor("#4CAF50"))
                    statusBadge.background.setTint(Color.parseColor("#E8F5E9"))
                }
                "idle" -> {
                    statusBadge.setTextColor(Color.parseColor("#F9A825"))
                    statusBadge.background.setTint(Color.parseColor("#FFF9C4"))
                }
                else -> { // offline
                    statusBadge.setTextColor(Color.parseColor("#9E9E9E"))
                    statusBadge.background.setTint(Color.parseColor("#F5F5F5"))
                }
            }

            // Estimate Purok based on coordinates (simplified for demo)
            card.findViewById<TextView>(R.id.tv_location).text = "Barangay Balintawak"

            card.setOnClickListener {
                if (selectedTruckId == truck.truckId) {
                    selectedTruckId = null
                    mapFragment?.setSelectedTruck(null)
                    coverageSection.visibility = View.GONE
                } else {
                    selectedTruckId = truck.truckId
                    mapFragment?.setSelectedTruck(truck.truckId)
                    mapFragment?.updateTrucks(listOf(truck), autoCenterOnFirst = false)
                }
                // Refresh list to show selection highlight
                updateTruckList(trucks)
            }

            // History Button Toggle
            val btnHistory = card.findViewById<MaterialButton>(R.id.btn_view_history)
            btnHistory.setOnClickListener {
                isHistoryActive = !isHistoryActive
                mapFragment?.setSelectedTruck(if (isHistoryActive) truck.truckId else null)
                btnHistory.backgroundTintList = ColorStateList.valueOf(
                    if (isHistoryActive) Color.parseColor("#E5E5EA") else Color.parseColor("#F2F2F7")
                )
            }

            // Optimize/Compare Button Toggle
            val btnOptimize = card.findViewById<MaterialButton>(R.id.btn_optimize)
            btnOptimize.setOnClickListener {
                isOptimizationActive = !isOptimizationActive
                mapFragment?.toggleOptimization(truck.truckId, isOptimizationActive)
                btnOptimize.text = if (isOptimizationActive) "HIDE PATH" else "COMPARE PATH"
                btnOptimize.backgroundTintList = ColorStateList.valueOf(
                    if (isOptimizationActive) Color.parseColor("#FF9500") else Color.parseColor("#34C759")
                )
            }
            
            // ETA Calculation
            val tvEta = card.findViewById<TextView>(R.id.btn_eta)
            if (truck.latitude != 0.0) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(truck.latitude, truck.longitude, 13.955805, 121.158888, results)
                val distanceMeters = results[0].toDouble()
                val etaSeconds = com.example.myapplication.utils.PredictionEngine.predictArrivalTime(distanceMeters, emptyList())
                val minutes = (etaSeconds / 60).toInt()
                tvEta.text = if (minutes > 0) "ETA: $minutes mins" else "ETA: < 1 min"
            } else {
                tvEta.text = "ETA: --"
            }

            truckListContainer.addView(card)
        }
    }

    private fun updateCoverageUI(truckId: String) {
        coverageSection.visibility = View.VISIBLE
        purokChecklistContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val coverage = mapFragment?.getCoverage(truckId) ?: emptyList()
        coverage.forEach { item ->
            val itemView = inflater.inflate(R.layout.item_purok_check, purokChecklistContainer, false)
            itemView.findViewById<TextView>(R.id.tv_purok_name).text = item.name
            itemView.findViewById<TextView>(R.id.tv_completion_time).text = item.completionTime ?: "Not visited yet"
            
            val statusText = itemView.findViewById<TextView>(R.id.tv_status_text)
            val icon = itemView.findViewById<android.widget.ImageView>(R.id.iv_check_status)
            
            if (item.isCompleted) {
                statusText.text = "COMPLETED"
                statusText.background.setTint(Color.parseColor("#E8F5E9"))
                statusText.setTextColor(Color.parseColor("#4CAF50"))
                icon.setColorFilter(Color.parseColor("#4CAF50"))
            } else {
                statusText.text = "PENDING"
                statusText.background.setTint(Color.parseColor("#F5F5F5"))
                statusText.setTextColor(Color.parseColor("#9E9E9E"))
                icon.setColorFilter(Color.parseColor("#CCCCCC"))
            }
            
            purokChecklistContainer.addView(itemView)
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val userRole = sessionManager.getUser()?.role ?: "resident"

        bottomNav.menu.clear()
        if (userRole == "admin") {
            bottomNav.inflateMenu(R.menu.admin_bottom_nav_menu)
        } else {
            bottomNav.inflateMenu(R.menu.resident_bottom_nav_menu)
        }

        bottomNav.selectedItemId = R.id.nav_track

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_track -> true
                R.id.nav_complaints -> {
                    val intent = if (userRole == "admin") Intent(this, ComplaintsActivity::class.java) 
                                 else Intent(this, ResidentComplaintsActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    val intent = if (userRole == "admin") Intent(this, AdminSettingsActivity::class.java) 
                                 else Intent(this, ResidentSettingsActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_home, R.id.nav_monitor -> {
                    val intent = if (userRole == "admin") Intent(this, AdminDashboardActivity::class.java) 
                                 else Intent(this, ResidentDashboardActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_reports -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
