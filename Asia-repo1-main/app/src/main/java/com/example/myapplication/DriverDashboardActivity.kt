package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.fragments.MapboxFragment
import com.example.myapplication.network.LocationUpdateService
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.GpsStatusMonitor
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class DriverDashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvDriverName: TextView
    private lateinit var tvTruckId: TextView
    private lateinit var tvCurrentStatus: TextView
    
    private lateinit var layoutDashboard: android.view.View
    private lateinit var layoutMap: android.view.View
    private lateinit var layoutSettings: android.view.View
    private lateinit var bottomNav: BottomNavigationView

    // Settings tab views
    private lateinit var tvSettingsProfileName: TextView
    private lateinit var tvSettingsProfileContact: TextView
    private lateinit var tvSettingsProfileTruck: TextView
    
    private var mapFragment: MapboxFragment? = null

    private var activeDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Continuous GPS Monitoring
        lifecycle.addObserver(GpsStatusMonitor(this) { isEnabled ->
            if (!isEnabled) {
                mapFragment?.clearMap()
            }
        })

        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driver_dashboard_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        initializeViews()
        setupNavigation()
        setupStatusControls()
        setupSettingsTab()
        setupSettingsClickListeners()
        setupMap(isFullMode = false)
        checkLocationPermissions()
        
        // Initial setup for Dashboard
        val user = sessionManager.getUser()
        tvDriverName.text = user?.name ?: "Pedro Santos"
        tvTruckId.text = "Truck: ${user?.preferredTruck ?: "GT-001"}"
    }

    private fun initializeViews() {
        tvDriverName = findViewById(R.id.tvDriverName)
        tvTruckId = findViewById(R.id.tvTruckId)
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus)
        
        layoutDashboard = findViewById(R.id.layout_dashboard)
        layoutMap = findViewById(R.id.layout_map)
        layoutSettings = findViewById(R.id.layout_settings)
        bottomNav = findViewById(R.id.bottom_navigation)

        // Settings view references
        tvSettingsProfileName = findViewById(R.id.tv_settings_profile_name)
        tvSettingsProfileContact = findViewById(R.id.tv_settings_profile_contact)
        tvSettingsProfileTruck = findViewById(R.id.tv_settings_profile_truck)
        
        findViewById<android.view.View>(R.id.btn_switch_to_map).setOnClickListener {
            switchToTab(R.id.nav_map)
        }
        
        findViewById<android.view.View>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<android.view.View>(R.id.btn_settings_logout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupSettingsTab() {
        val user = sessionManager.getUser()
        tvSettingsProfileName.text = user?.name ?: "Pedro Santos"
        tvSettingsProfileContact.text = user?.phone ?: "09191234567"
        tvSettingsProfileTruck.text = user?.preferredTruck ?: "GT-001"
    }

    private fun setupSettingsClickListeners() {
        // Route Management
        findViewById<android.view.View>(R.id.ll_settings_view_daily_routes).setOnClickListener {
            showSettingsModal(R.layout.dialog_daily_routes)
        }
        findViewById<android.view.View>(R.id.ll_settings_route_history).setOnClickListener {
            showSettingsModal(R.layout.dialog_route_history)
        }
        findViewById<android.view.View>(R.id.ll_settings_performance_stats).setOnClickListener {
            showSettingsModal(R.layout.dialog_performance_stats)
        }

        // Truck Information
        findViewById<android.view.View>(R.id.ll_settings_truck_details).setOnClickListener {
            showSettingsModal(R.layout.dialog_truck_details)
        }
        findViewById<android.view.View>(R.id.ll_settings_maintenance_schedule).setOnClickListener {
            showSettingsModal(R.layout.dialog_maintenance_schedule)
        }
        findViewById<android.view.View>(R.id.ll_settings_report_issue).setOnClickListener {
            showSettingsModal(R.layout.dialog_report_truck_issue)
        }

        // Notifications
        findViewById<android.view.View>(R.id.ll_settings_notification_preferences).setOnClickListener {
            showSettingsModal(R.layout.dialog_notification_preferences)
        }
        findViewById<android.view.View>(R.id.ll_settings_alert_history).setOnClickListener {
            showSettingsModal(R.layout.dialog_alert_history)
        }
    }

    private fun showSettingsModal(layoutResId: Int) {
        if (isFinishing || isDestroyed) return
        
        try {
            val dialogView = LayoutInflater.from(this).inflate(layoutResId, null)
            val alertDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()
            activeDialog = alertDialog

            alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener {
                alertDialog.dismiss()
            }

            alertDialog.show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Module coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigation() {
        bottomNav.selectedItemId = R.id.nav_dashboard
        bottomNav.setOnItemSelectedListener { item ->
            switchToTab(item.itemId)
            true
        }
    }

    private fun switchToTab(itemId: Int) {
        layoutDashboard.visibility = if (itemId == R.id.nav_dashboard) android.view.View.VISIBLE else android.view.View.GONE
        layoutMap.visibility = if (itemId == R.id.nav_map) android.view.View.VISIBLE else android.view.View.GONE
        layoutSettings.visibility = if (itemId == R.id.nav_settings) android.view.View.VISIBLE else android.view.View.GONE
        
        bottomNav.menu.findItem(itemId).isChecked = true
        
        if (itemId == R.id.nav_map) {
            setupMap(isFullMode = true)
        } else if (itemId == R.id.nav_dashboard) {
            setupMap(isFullMode = false)
        }
    }

    private fun setupMap(isFullMode: Boolean) {
        val mode = if (isFullMode) MapboxFragment.MODE_FULL else MapboxFragment.MODE_DASHBOARD
        
        // Find the container ID
        val containerId = if (isFullMode) {
            R.id.map_fragment_container_full
        } else {
            R.id.map_fragment_container
        }

        // Check if mapFragment already exists
        if (mapFragment == null) {
            mapFragment = MapboxFragment.newInstance(mode)
            supportFragmentManager.beginTransaction()
                .replace(containerId, mapFragment!!)
                .commit()
        } else {
            // Remove from old parent and add to new parent
            val currentFragment = mapFragment!!
            supportFragmentManager.beginTransaction().remove(currentFragment).commitNow()
            
            // Re-create to ensure mode is applied (or you could add a setMode method to Fragment)
            mapFragment = MapboxFragment.newInstance(mode)
            supportFragmentManager.beginTransaction()
                .replace(containerId, mapFragment!!)
                .commit()
        }
    }

    private fun setupStatusControls() {
        findViewById<android.view.View>(R.id.btn_start).setOnClickListener {
            checkLocationPermissions {
                tvCurrentStatus.text = "ACTIVE"
                tvCurrentStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                startService(Intent(this, LocationUpdateService::class.java))
            }
        }
        findViewById<android.view.View>(R.id.btn_pause).setOnClickListener {
            tvCurrentStatus.text = "PAUSED"
            tvCurrentStatus.setTextColor(android.graphics.Color.parseColor("#FFA000"))
            stopService(Intent(this, LocationUpdateService::class.java))
        }
        findViewById<android.view.View>(R.id.btn_finish).setOnClickListener {
            tvCurrentStatus.text = "COMPLETED"
            tvCurrentStatus.setTextColor(android.graphics.Color.parseColor("#1976D2"))
            stopService(Intent(this, LocationUpdateService::class.java))
        }
    }

    private fun checkLocationPermissions(onGranted: (() -> Unit)? = null) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
        } else {
            onGranted?.invoke()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, if they clicked start it will need another click or we can auto-start
        } else {
            android.widget.Toast.makeText(this, "Location permission is required for tracking", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutConfirmation() {
        if (isFinishing || isDestroyed) return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        activeDialog = alertDialog

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_confirm_logout).setOnClickListener {
            sessionManager.logout()
            stopService(Intent(this, LocationUpdateService::class.java))
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        alertDialog.show()
    }

    override fun onDestroy() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroy()
    }
}
