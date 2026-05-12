package com.example.myapplication

import android.content.Intent
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

class TrackTrucksActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var truckListContainer: LinearLayout
    private lateinit var routeProgress: LinearProgressIndicator
    private var mapFragment: MapboxFragment? = null
    private var isGpsActive: Boolean = true
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L // 5 seconds

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isGpsActive) {
                fetchTruckLocations()
            }
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

        setupBottomNavigation()
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
        truckListContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        trucks.forEach { truck ->
            val card = inflater.inflate(R.layout.item_truck_card, truckListContainer, false)
            
            card.findViewById<TextView>(R.id.tv_truck_id).text = truck.truckId
            card.findViewById<TextView>(R.id.tv_plate_number).text = truck.plateNumber ?: "N/A"
            card.findViewById<TextView>(R.id.tv_driver_name).text = truck.driverName ?: "Unknown"
            card.findViewById<TextView>(R.id.tv_speed).text = "${truck.speed.toInt()} km/h"
            card.findViewById<TextView>(R.id.tv_last_update).text = "🕒 Last Update: ${truck.updatedAt}"
            
            val statusBadge = card.findViewById<TextView>(R.id.tv_status_badge)
            statusBadge.text = truck.status.uppercase()
            
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
                mapFragment?.updateTrucks(listOf(truck), autoCenterOnFirst = false)
            }

            truckListContainer.addView(card)
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
                R.id.nav_users -> {
                    startActivity(Intent(this, UserManagementActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
