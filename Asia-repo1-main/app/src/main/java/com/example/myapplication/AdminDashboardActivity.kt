    package com.example.myapplication

    import android.content.Intent
    import android.os.Bundle
    import android.os.Handler
    import android.os.Looper
    import android.view.LayoutInflater
    import android.widget.Button
    import android.widget.TextView
    import androidx.activity.enableEdgeToEdge
    import androidx.appcompat.app.AlertDialog
    import androidx.appcompat.app.AppCompatActivity
    import androidx.core.view.ViewCompat
    import androidx.core.view.WindowInsetsCompat
    import com.example.myapplication.fragments.MapboxFragment
    import com.example.myapplication.models.ComplaintsResponse
    import com.example.myapplication.network.RetrofitClient
    import com.example.myapplication.utils.SessionManager
    import com.example.myapplication.utils.GpsStatusMonitor
    import com.example.myapplication.network.LocationUpdateService
    import com.google.android.material.bottomnavigation.BottomNavigationView
    import android.Manifest
    import android.content.pm.PackageManager
    import androidx.core.app.ActivityCompat
    import com.google.firebase.database.*
    import retrofit2.Call
    import retrofit2.Callback
    import retrofit2.Response

    class AdminDashboardActivity : AppCompatActivity() {
        private lateinit var sessionManager: SessionManager
        private lateinit var tvActiveTrucks: TextView
        private lateinit var tvComplaintsCount: TextView
        private lateinit var tvResidentsCount: TextView
        private lateinit var tvCoveragePercent: TextView
        private lateinit var badgeComplaints: TextView
        private lateinit var tvRoutesCompleted: TextView

        private var mapFragment: MapboxFragment? = null
        private var isGpsActive: Boolean = true

        // Handler specifically for complaints count
        private val complaintsHandler = Handler(Looper.getMainLooper())
        private val complaintsRunnable = object : Runnable {
            override fun run() {
                fetchComplaintsCount()
                complaintsHandler.postDelayed(this, 10000L)
            }
        }

        private var logoutDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sessionManager = SessionManager(this)

            // Continuous GPS Monitoring
            lifecycle.addObserver(GpsStatusMonitor(this) { isEnabled ->
                isGpsActive = isEnabled
                if (!isEnabled) {
                    mapFragment?.clearMap() // Pause map visuals
                }
            })

            enableEdgeToEdge()
            setContentView(R.layout.activity_admin_dashboard)

            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.admin_dashboard_root)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            initializeViews()
            setupMap()
            setupBottomNavigation()
            setupClickListeners()
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

        private fun initializeViews() {
            tvActiveTrucks = findViewById(R.id.tv_active_trucks)
            tvComplaintsCount = findViewById(R.id.tv_complaints_count)
            tvResidentsCount = findViewById(R.id.tv_residents_count)
            tvCoveragePercent = findViewById(R.id.tv_coverage_percent)
            badgeComplaints = findViewById(R.id.badge_complaints)
            tvRoutesCompleted = findViewById(R.id.tv_routes_completed)

            // Initial mock values as per design
            tvResidentsCount.text = "3"
            tvCoveragePercent.text = "85%"
            tvRoutesCompleted.text = "5 / 8"
        }

        private fun setupMap() {
            mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment_container) as? MapboxFragment
            if (mapFragment == null) {
                mapFragment = MapboxFragment.newInstance(MapboxFragment.MODE_DASHBOARD)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.map_fragment_container, mapFragment!!)
                    .commit()
            }
        }

        private fun setupClickListeners() {
            findViewById<TextView>(R.id.tv_full_map).setOnClickListener {
                startActivity(Intent(this, TrackTrucksActivity::class.java))
            }

            findViewById<android.view.View>(R.id.btn_logout).setOnClickListener {
                showLogoutConfirmation()
            }

            findViewById<android.view.View>(R.id.row_analytics).setOnClickListener {
                startActivity(Intent(this, AnalyticsActivity::class.java))
                overridePendingTransition(0, 0)
            }

            findViewById<android.view.View>(R.id.row_residents).setOnClickListener {
                val intent = Intent(this, UserManagementActivity::class.java)
                intent.putExtra("TAB_INDEX", 0)
                startActivity(intent)
                overridePendingTransition(0, 0)
            }

            findViewById<android.view.View>(R.id.row_complaints).setOnClickListener {
                startActivity(Intent(this, ComplaintsActivity::class.java))
                overridePendingTransition(0, 0)
            }

            findViewById<android.view.View>(R.id.row_registrations).setOnClickListener {
                val intent = Intent(this, UserManagementActivity::class.java)
                intent.putExtra("TAB_INDEX", 2)
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
        }

        override fun onResume() {
            super.onResume()
            complaintsHandler.post(complaintsRunnable)
        }

        override fun onPause() {
            super.onPause()
            complaintsHandler.removeCallbacks(complaintsRunnable)
        }

        private fun fetchComplaintsCount() {
            RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
                override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val complaints = response.body()?.data ?: emptyList()
                        val pendingCount = complaints.count { it.status.lowercase() == "pending" }
                        tvComplaintsCount.text = pendingCount.toString()
                        badgeComplaints.text = pendingCount.toString()
                        badgeComplaints.visibility = if (pendingCount > 0) android.view.View.VISIBLE else android.view.View.GONE
                    }
                }
                override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {}
            })
        }

    private fun showLogoutConfirmation() {
        if (isFinishing || isDestroyed) return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation, null)
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

        private fun setupBottomNavigation() {
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNav.selectedItemId = R.id.nav_monitor

            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_monitor -> true
                    R.id.nav_track -> {
                        startActivity(Intent(this, TrackTrucksActivity::class.java))
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
                    R.id.nav_complaints -> {
                        startActivity(Intent(this, ComplaintsActivity::class.java))
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
                    R.id.nav_settings -> {
                        startActivity(Intent(this, AdminSettingsActivity::class.java))
                        overridePendingTransition(0, 0)
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }
    }
