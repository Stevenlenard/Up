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
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.widget.EditText
import androidx.core.app.ActivityCompat
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

class DriverDashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvDriverName: TextView
    private lateinit var tvTruckId: TextView
    private lateinit var tvCurrentStatus: TextView
    
    private lateinit var layoutDashboard: android.view.View
    private lateinit var layoutMap: android.view.View
    private lateinit var layoutSettings: android.view.View
    private lateinit var bottomNav: BottomNavigationView

    // Trip Info View References (Dashboard)
    private lateinit var tvPlateNumberValue: TextView
    private lateinit var tvStartTimeValue: TextView
    private lateinit var tvEstimatedEndValue: TextView
    private lateinit var tvTotalDistanceValue: TextView

    // Trip Info View References (Bottom Sheet)
    private lateinit var tvPlateNumberValueSheet: TextView
    private lateinit var tvStartTimeValueSheet: TextView
    private lateinit var tvEstimatedEndValueSheet: TextView
    private lateinit var tvTotalDistanceValueSheet: TextView

    // Settings tab views
    private lateinit var tvSettingsProfileName: TextView
    private lateinit var tvSettingsProfileContact: TextView
    private lateinit var tvSettingsProfileTruck: TextView
    
    private var mapFragment: MapboxFragment? = null
    private var activeDialog: AlertDialog? = null

    // GPS & Navigation Switches
    private lateinit var swGpsTracking: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var swRouteAlerts: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var tvGpsWarning: TextView

    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        setupTripInfoEdits()
        setupSettingsTab()
        setupSettingsClickListeners()
        setupSettingsSwitches()
        setupQuickActions()
        setupDemoControls()
        setupMap(isFullMode = false)
        checkLocationPermissions()
        listenForTripInfoChanges()
    }

    private fun initializeViews() {
        tvDriverName = findViewById(R.id.tvDriverName)
        tvTruckId = findViewById(R.id.tvTruckId)
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus)
        
        // Trip Info (Dashboard)
        tvPlateNumberValue = findViewById(R.id.tvPlateNumberValue)
        tvStartTimeValue = findViewById(R.id.tvStartTimeValue)
        tvEstimatedEndValue = findViewById(R.id.tvEstimatedEndValue)
        tvTotalDistanceValue = findViewById(R.id.tvTotalDistanceValue)

        // Trip Info (Bottom Sheet)
        tvPlateNumberValueSheet = findViewById(R.id.tvPlateNumberValueSheet)
        tvStartTimeValueSheet = findViewById(R.id.tvStartTimeValueSheet)
        tvEstimatedEndValueSheet = findViewById(R.id.tvEstimatedEndValueSheet)
        tvTotalDistanceValueSheet = findViewById(R.id.tvTotalDistanceValueSheet)

        layoutDashboard = findViewById(R.id.layout_dashboard)
        layoutMap = findViewById(R.id.layout_map)
        layoutSettings = findViewById(R.id.layout_settings)
        bottomNav = findViewById(R.id.bottom_navigation)

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

    private fun setupTripInfoEdits() {
        val user = sessionManager.getUser()
        val truckId = user?.preferredTruck ?: "GT-001"
        val database = FirebaseDatabase.getInstance(dbUrl)
        val truckRef = database.getReference("truck_locations").child(truckId).child("trip_info")

        // Plate Number Click Listener
        findViewById<android.view.View>(R.id.rlPlateNumber).setOnClickListener {
            val editText = EditText(this)
            editText.setText(tvPlateNumberValue.text)
            editText.setPadding(50, 40, 50, 40)
            
            AlertDialog.Builder(this)
                .setTitle("Update Plate Number")
                .setMessage("Enter the correct plate number for this trip.")
                .setView(editText)
                .setPositiveButton("Update") { _, _ ->
                    val newPlate = editText.text.toString().trim()
                    if (newPlate.isNotEmpty()) {
                        truckRef.child("plateNumber").setValue(newPlate)
                        android.widget.Toast.makeText(this, "Plate number updated", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Start Time Click Listener
        findViewById<android.view.View>(R.id.rlStartTime).setOnClickListener {
            showTimePicker { time ->
                truckRef.child("startTime").setValue(time)
                android.widget.Toast.makeText(this, "Start time updated", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Estimated End Click Listener
        findViewById<android.view.View>(R.id.rlEstimatedEnd).setOnClickListener {
            showTimePicker { time ->
                truckRef.child("estimatedEnd").setValue(time)
                android.widget.Toast.makeText(this, "Estimated end time updated", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val amPm = if (selectedHour < 12) "AM" else "PM"
            val hourDisplay = if (selectedHour % 12 == 0) 12 else selectedHour % 12
            val time = String.format("%d:%02d %s", hourDisplay, selectedMinute, amPm)
            onTimeSelected(time)
        }, hour, minute, false).show()
    }

    private fun listenForTripInfoChanges() {
        val user = sessionManager.getUser()
        val truckId = user?.preferredTruck ?: "GT-001"
        FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations").child(truckId).child("trip_info")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val plate = snapshot.child("plateNumber").getValue(String::class.java)
                    val start = snapshot.child("startTime").getValue(String::class.java)
                    val end = snapshot.child("estimatedEnd").getValue(String::class.java)
                    val distance = snapshot.child("totalDistance").getValue(String::class.java)

                    plate?.let {
                        tvPlateNumberValue.text = it
                        tvPlateNumberValueSheet.text = it
                    }
                    start?.let {
                        tvStartTimeValue.text = it
                        tvStartTimeValueSheet.text = it
                    }
                    end?.let {
                        tvEstimatedEndValue.text = it
                        tvEstimatedEndValueSheet.text = it
                    }
                    distance?.let {
                        tvTotalDistanceValue.text = it
                        tvTotalDistanceValueSheet.text = it
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private fun setupQuickActions() {
        findViewById<android.view.View>(R.id.cardViewMap).setOnClickListener {
            switchToTab(R.id.nav_map)
        }
        findViewById<android.view.View>(R.id.cardFileIssue).setOnClickListener {
            showSettingsModal(R.layout.dialog_report_truck_issue)
        }
        findViewById<android.view.View>(R.id.cardRateService).setOnClickListener {
            android.widget.Toast.makeText(this, "Feedback module coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDemoControls() {
        val user = sessionManager.getUser()
        val truckId = user?.preferredTruck ?: "GT-001"
        val driverName = user?.name ?: "Pedro Santos"
        val database = FirebaseDatabase.getInstance(dbUrl)

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

        val user2 = sessionManager.getUser()
        tvDriverName.text = user2?.name ?: "Pedro Santos"
        tvTruckId.text = "Truck: ${user2?.preferredTruck ?: "GT-001"}"
    }

    private fun setupSettingsTab() {
        val user = sessionManager.getUser()
        tvSettingsProfileName.text = user?.name ?: "Pedro Santos"
        tvSettingsProfileContact.text = user?.phone ?: "09191234567"
        tvSettingsProfileTruck.text = user?.preferredTruck ?: "GT-001"
    }

    private fun setupSettingsClickListeners() {
        findViewById<android.view.View>(R.id.ll_settings_view_daily_routes).setOnClickListener {
            showSettingsModal(R.layout.dialog_daily_routes)
        }
        findViewById<android.view.View>(R.id.ll_settings_route_history).setOnClickListener {
            showSettingsModal(R.layout.dialog_route_history)
        }
        findViewById<android.view.View>(R.id.ll_settings_performance_stats).setOnClickListener {
            showSettingsModal(R.layout.dialog_performance_stats)
        }
        findViewById<android.view.View>(R.id.ll_settings_truck_details).setOnClickListener {
            showSettingsModal(R.layout.dialog_truck_details)
        }
        findViewById<android.view.View>(R.id.ll_settings_maintenance_schedule).setOnClickListener {
            showSettingsModal(R.layout.dialog_maintenance_schedule)
        }
        findViewById<android.view.View>(R.id.ll_settings_report_issue).setOnClickListener {
            showSettingsModal(R.layout.dialog_report_truck_issue)
        }
        findViewById<android.view.View>(R.id.ll_settings_notification_preferences).setOnClickListener {
            showSettingsModal(R.layout.dialog_notification_preferences)
        }
        findViewById<android.view.View>(R.id.ll_settings_alert_history).setOnClickListener {
            showSettingsModal(R.layout.dialog_alert_history)
        }
    }

    private fun setupSettingsSwitches() {
        swGpsTracking = findViewById(R.id.swGpsTracking)
        swRouteAlerts = findViewById(R.id.swRouteAlerts)
        tvGpsWarning = findViewById(R.id.tvGpsWarning)

        swGpsTracking.setOnCheckedChangeListener { _, isChecked ->
            tvGpsWarning.visibility = if (isChecked) android.view.View.GONE else android.view.View.VISIBLE
        }

        swRouteAlerts.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                android.widget.Toast.makeText(this, "✅ You will be notified if the routes are changed", android.widget.Toast.LENGTH_SHORT).show()
            }
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
                        FirebaseDatabase.getInstance(dbUrl).getReference("notifications").push().setValue(notification)
                        com.example.myapplication.utils.CustomNotification.showTopNotification(this, "Issue report submitted to Admin", false)
                        alertDialog.dismiss()
                    } else {
                        android.widget.Toast.makeText(this, "Please fill all fields", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            dialogView.findViewById<android.view.View>(R.id.btn_cancel)?.setOnClickListener { alertDialog.dismiss() }
            dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener { alertDialog.dismiss() }
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
        val containerId = if (isFullMode) R.id.map_fragment_container_full else R.id.map_fragment_container
        if (mapFragment == null) {
            mapFragment = MapboxFragment.newInstance(mode)
            supportFragmentManager.beginTransaction().replace(containerId, mapFragment!!).commit()
        } else {
            val currentFragment = mapFragment!!
            supportFragmentManager.beginTransaction().remove(currentFragment).commitNow()
            mapFragment = MapboxFragment.newInstance(mode)
            supportFragmentManager.beginTransaction().replace(containerId, mapFragment!!).commit()
        }
    }

    private fun setupStatusControls() {
        val user = sessionManager.getUser()
        val truckId = user?.preferredTruck ?: "GT-001"
        val database = FirebaseDatabase.getInstance(dbUrl)

        findViewById<android.view.View>(R.id.btn_start).setOnClickListener {
            checkLocationPermissions {
                tvCurrentStatus.text = "ACTIVE"
                tvCurrentStatus.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                tvCurrentStatus.setTextColor(android.graphics.Color.WHITE)
                startService(Intent(this, LocationUpdateService::class.java))
                database.getReference("truck_locations").child(truckId).child("status").setValue("active")
                database.getReference("truck_locations").child(truckId).child("isFull").setValue(false)
            }
        }
        findViewById<android.view.View>(R.id.btn_pause).setOnClickListener {
            tvCurrentStatus.text = "PAUSED (IDLE)"
            tvCurrentStatus.setBackgroundColor(android.graphics.Color.parseColor("#FFC107"))
            tvCurrentStatus.setTextColor(android.graphics.Color.BLACK)
            database.getReference("truck_locations").child(truckId).child("status").setValue("idle")
        }
        findViewById<android.view.View>(R.id.btn_full).setOnClickListener {
            tvCurrentStatus.text = "FULL"
            tvCurrentStatus.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
            tvCurrentStatus.setTextColor(android.graphics.Color.WHITE)
            database.getReference("truck_locations").child(truckId).child("isFull").setValue(true)
            database.getReference("truck_locations").child(truckId).child("status").setValue("full")
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val logRef = database.getReference("collection_logs").push()
            logRef.setValue(mapOf("truckId" to truckId, "timestamp" to System.currentTimeMillis(), "type" to "FULL", "date" to today))
            android.widget.Toast.makeText(this, "Truck marked as FULL. Notifications stopped.", android.widget.Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btn_finish).setOnClickListener {
            tvCurrentStatus.text = "COMPLETED"
            tvCurrentStatus.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
            tvCurrentStatus.setTextColor(android.graphics.Color.WHITE)
            database.getReference("truck_locations").child(truckId).child("status").setValue("completed")
            database.getReference("truck_locations").child(truckId).child("isFull").setValue(false)
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
        if (!(requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            android.widget.Toast.makeText(this, "Location permission is required for tracking", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutConfirmation() {
        if (isFinishing || isDestroyed) return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation, null)
        val alertDialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create()
        activeDialog = alertDialog
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener { alertDialog.dismiss() }
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
