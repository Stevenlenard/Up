package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.os.Build
import com.google.firebase.database.FirebaseDatabase
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView

class ResidentDashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var tvWelcome: TextView
    private lateinit var tvUserPurok: TextView
    private lateinit var tvActiveTrucksCount: TextView
    private lateinit var tvEstimatedTime: TextView
    private var mapFragment: MapboxFragment? = null
    private var isGpsActive: Boolean = true

    private var logoutDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Continuous GPS Monitoring
        lifecycle.addObserver(GpsStatusMonitor(this) { isEnabled ->
            isGpsActive = isEnabled
            if (!isEnabled) {
                mapFragment?.clearMap() // Pause map visuals
            }
        })

        enableEdgeToEdge()
        setContentView(R.layout.activity_resident_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resident_dashboard_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvUserPurok = findViewById(R.id.tvUserPurok)
        tvActiveTrucksCount = findViewById(R.id.tvActiveTrucksCount)
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime)

        val user = sessionManager.getUser()
        tvWelcome.text = user?.name ?: "Juan Dela Cruz"
        tvUserPurok.text = user?.purok ?: "Purok 2"

        mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment_container) as? MapboxFragment
        if (mapFragment == null) {
            mapFragment = MapboxFragment.newInstance(MapboxFragment.MODE_DASHBOARD)
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment_container, mapFragment!!)
                .commit()
        }

        setupClickListeners()
        setupTestTrigger()
        setupBottomNavigation()
        setupLogout()
        checkLocationPermissions()
    }

    private fun checkLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1001)
        } else {
            startLocationService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationService()
        }
    }

    private fun startLocationService() {
        startService(Intent(this, LocationUpdateService::class.java))
    }

    private fun setupTestTrigger() {
        findViewById<MaterialCardView>(R.id.cardNearbyAlert).setOnClickListener {
            // Check Notification Permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Please allow notifications in settings", Toast.LENGTH_LONG).show()
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                    return@setOnClickListener
                }
            }

            val user = sessionManager.getUser() ?: return@setOnClickListener
            val purok = user.purok ?: "Purok 2"
            val driverName = "John Driver"
            val truckId = "GT-777"
            val lat = 13.9402
            val lng = 121.1638
            
            val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
            val db = FirebaseDatabase.getInstance(dbUrl)
            
            // 1. Send Alert (for Notification) - Simulating an Arrival Alert
            val alertData = mapOf(
                "message" to "The garbage truck has arrived at $purok. Please bring out your trash!",
                "timestamp" to System.currentTimeMillis(),
                "driver" to driverName,
                "truck_id" to truckId,
                "latitude" to lat,
                "longitude" to lng,
                "type" to "ARRIVAL_ALERT"
            )
            db.getReference("alerts").child(purok).setValue(alertData)
            
            // 2. Update Map Position (for real-time map marker)
            val truckData = mapOf(
                "truckId" to truckId,
                "driverName" to driverName,
                "latitude" to lat,
                "longitude" to lng,
                "speed" to 15.5,
                "isFull" to false,
                "status" to "active",
                "updatedAt" to System.currentTimeMillis().toString()
            )
            db.getReference("truck_locations").child(truckId).setValue(truckData)
            
            Toast.makeText(this, "Simulating $truckId nearby...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLogout() {
        findViewById<View>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLogoutConfirmation() {
        if (isFinishing || isDestroyed) return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation_resident, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        logoutDialog = alertDialog

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
        logoutDialog?.dismiss()
        logoutDialog = null
        super.onDestroy()
    }

    private fun setupClickListeners() {
        findViewById<MaterialCardView>(R.id.cardTrackTruckQuick).setOnClickListener {
            startActivity(Intent(this, TrackTrucksActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardFileComplaintQuick).setOnClickListener {
            startActivity(Intent(this, ResidentComplaintsActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardRateService).setOnClickListener {
            showFeedbackDialog()
        }

        findViewById<TextView>(R.id.tvFullMap).setOnClickListener {
            startActivity(Intent(this, TrackTrucksActivity::class.java))
        }
    }

    private fun showFeedbackDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_feedback, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ratingBar = dialogView.findViewById<android.widget.RatingBar>(R.id.ratingBar)
        val etFeedback = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFeedback)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitFeedback)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        btnCancel.setOnClickListener { alertDialog.dismiss() }

        btnSubmit.setOnClickListener {
            val rating = ratingBar.rating
            val feedbackText = etFeedback.text.toString()
            val user = sessionManager.getUser()

            val feedbackData = mapOf(
                "userId" to (user?.userId ?: 0),
                "userName" to (user?.name ?: "Anonymous"),
                "rating" to rating,
                "feedback" to feedbackText,
                "timestamp" to System.currentTimeMillis()
            )

            val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
            FirebaseDatabase.getInstance(dbUrl).getReference("user_evaluations").push().setValue(feedbackData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                    alertDialog.dismiss()
                }
        }

        alertDialog.show()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_track -> {
                    startActivity(Intent(this, TrackTrucksActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ResidentComplaintsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, ResidentSettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
