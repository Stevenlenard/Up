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
        setupDemoControls()
        setupMap(isFullMode = false)
        checkLocationPermissions()
    }

    private fun setupDemoControls() {
        val user = sessionManager.getUser()
        val truckId = user?.preferredTruck ?: "GT-001"
        val driverName = user?.name ?: "Pedro Santos"
        val database = com.google.firebase.database.FirebaseDatabase.getInstance("https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app")

        findViewById<android.view.View>(R.id.btn_manual_alert).setOnClickListener {
            val zones = com.example.myapplication.utils.PurokManager.purokZones.map { it.name }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Send Manual Alert")
                .setItems(zones) { _, which ->
                    val selectedPurok = zones[which]
                    val alertData = mapOf(
                        "message" to "🚛 Manual Alert: The garbage truck is heading to $selectedPurok. Please prepare your trash!",
                        "timestamp" to System.currentTimeMillis(),
                        "driver" to driverName,
                        "truck_id" to truckId,
                        "type" to "MANUAL_ALERT"
                    )
                    database.getReference("alerts").child(selectedPurok).setValue(alertData)
                    android.widget.Toast.makeText(this, "Alert sent to $selectedPurok", android.widget.Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        findViewById<android.view.View>(R.id.btn_demo_teleport).setOnClickListener {
            val zones = com.example.myapplication.utils.PurokManager.purokZones
            val zoneNames = zones.map { it.name }.toTypedArray()
            
            AlertDialog.Builder(this)
                .setTitle("Demo: Teleport Truck")
                .setItems(zoneNames) { _, which ->
                    val target = zones[which]
                    val truckData = mapOf(
                        "truckId" to truckId,
                        "driverName" to driverName,
                        "latitude" to target.latitude,
                        "longitude" to target.longitude,
                        "speed" to 20.0,
                        "isFull" to false,
                        "status" to "active",
                        "updatedAt" to System.currentTimeMillis().toString()
                    )
                    database.getReference("truck_locations").child(truckId).setValue(truckData)
                    android.widget.Toast.makeText(this, "Teleported to ${target.name}. Geofence logic triggered!", android.widget.Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        // Initial setup for Dashboard
        val user2 = sessionManager.getUser()
        tvDriverName.text = user2?.name ?: "Pedro Santos"
        tvTruckId.text = "Truck: ${user2?.preferredTruck ?: "GT-001"}"
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

            // Special logic for Report Issue
            if (layoutResId == R.layout.dialog_report_truck_issue) {
                val btnSubmit = dialogView.findViewById<android.widget.Button>(R.id.btn_submit_report)
                val etType = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_issue_type)
                val etDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_issue_description)
                
                btnSubmit.setOnClickListener {
                    val type = etType.text.toString()
                    val desc = etDesc.text.toString()
                    
                    if (type.isNotEmpty() && desc.isNotEmpty()) {
                        val user = sessionManager.getUser()
                        val notification = com.example.myapplication.models.SystemNotification(
                            type = "DRIVER_ISSUE",
                            title = "New Driver Issue: $type",
                            message = "${user?.name ?: "Driver"} reported an issue: $desc",
                            timestamp = System.currentTimeMillis(),
                            isRead = false,
                            relatedId = user?.userId?.toString() ?: ""
                        )
                        
                        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
                        com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                            .getReference("notifications").push().setValue(notification)
                            
                        com.example.myapplication.utils.CustomNotification.showTopNotification(this, "Issue report submitted to Admin", false)
                        alertDialog.dismiss()
                    } else {
                        android.widget.Toast.makeText(this, "Please fill all fields", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            dialogView.findViewById<android.view.View>(R.id.btn_cancel)?.setOnClickListener { alertDialog.dismiss() }
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
        val user = sessionManager.getUser()
        val truckId = user?.preferredTruck ?: "GT-001"
        val database = com.google.firebase.database.FirebaseDatabase.getInstance("https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app")

        findViewById<android.view.View>(R.id.btn_start).setOnClickListener {
            checkLocationPermissions {
                tvCurrentStatus.text = "ACTIVE"
                tvCurrentStatus.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")) // Green Background
                tvCurrentStatus.setTextColor(android.graphics.Color.WHITE)
                
                startService(Intent(this, LocationUpdateService::class.java))
                
                database.getReference("truck_locations").child(truckId).child("status").setValue("active")
                database.getReference("truck_locations").child(truckId).child("isFull").setValue(false)
            }
        }
        findViewById<android.view.View>(R.id.btn_pause).setOnClickListener {
            tvCurrentStatus.text = "PAUSED (IDLE)"
            tvCurrentStatus.setBackgroundColor(android.graphics.Color.parseColor("#FFC107")) // Yellow/Amber
            tvCurrentStatus.setTextColor(android.graphics.Color.BLACK)
            
            // We keep the service running to track location even while idle, but update status
            database.getReference("truck_locations").child(truckId).child("status").setValue("idle")
        }
        findViewById<android.view.View>(R.id.btn_full).setOnClickListener {
            tvCurrentStatus.text = "FULL"
            tvCurrentStatus.setBackgroundColor(android.graphics.Color.parseColor("#F44336")) // Red
            tvCurrentStatus.setTextColor(android.graphics.Color.WHITE)
            
            database.getReference("truck_locations").child(truckId).child("isFull").setValue(true)
            database.getReference("truck_locations").child(truckId).child("status").setValue("full")
            
            // Log full event for analytics
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val logRef = database.getReference("collection_logs").push()
            val logData = mapOf(
                "truckId" to truckId,
                "timestamp" to System.currentTimeMillis(),
                "type" to "FULL",
                "date" to today
            )
            logRef.setValue(logData)
            
            android.widget.Toast.makeText(this, "Truck marked as FULL. Notifications stopped.", android.widget.Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_finish).setOnClickListener {
            tvCurrentStatus.text = "COMPLETED"
            tvCurrentStatus.setBackgroundColor(android.graphics.Color.parseColor("#2196F3")) // Blue
            tvCurrentStatus.setTextColor(android.graphics.Color.WHITE)
            
            database.getReference("truck_locations").child(truckId).child("status").setValue("completed")
            database.getReference("truck_locations").child(truckId).child("isFull").setValue(false)
            
            // Stop service as the trip is done
            stopService(Intent(this, LocationUpdateService::class.java))
            
            // Note: status will be set to offline in LocationUpdateService.onDestroy()
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
