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
        setupBottomNavigation()
        setupLogout()
        checkLocationPermissions()
    }

    private fun checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
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

        findViewById<TextView>(R.id.tvFullMap).setOnClickListener {
            startActivity(Intent(this, TrackTrucksActivity::class.java))
        }
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
