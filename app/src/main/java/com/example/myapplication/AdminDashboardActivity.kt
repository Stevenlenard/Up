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
import android.widget.LinearLayout
import android.widget.Toast
import android.view.View
import androidx.core.app.ActivityCompat
import com.google.firebase.database.*
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.example.myapplication.adapters.TruckStatusAdapter
import com.example.myapplication.models.TruckLocation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
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
        private lateinit var progressRoutesCompleted: LinearProgressIndicator
        private lateinit var tvDistanceCovered: TextView
        private lateinit var tvComplaintsResolved: TextView
        private lateinit var badgeNotifications: TextView
        private lateinit var rvFleetStatus: RecyclerView
        private lateinit var fleetAdapter: TruckStatusAdapter

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

    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(dbUrl)
    private var truckListener: ValueEventListener? = null
    private var residentListener: ValueEventListener? = null
    private var coverageListener: ValueEventListener? = null
    private var notificationListener: ValueEventListener? = null

    private val notificationList = mutableListOf<com.example.myapplication.models.SystemNotification>()

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
            badgeNotifications = findViewById(R.id.badge_notification_count)
            
            progressRoutesCompleted = findViewById(R.id.progress_routes_completed)
            tvDistanceCovered = findViewById(R.id.tv_distance_covered)
            tvComplaintsResolved = findViewById(R.id.tv_complaints_resolved)

            // Initialize Fleet Status RecyclerView
            rvFleetStatus = findViewById(R.id.rv_fleet_status)
            fleetAdapter = TruckStatusAdapter(emptyList())
            rvFleetStatus.layoutManager = LinearLayoutManager(this)
            rvFleetStatus.adapter = fleetAdapter

            // Initial mock values as per design
            tvResidentsCount.text = "0"
            tvCoveragePercent.text = "0%"
            tvRoutesCompleted.text = "0 / 12"
            progressRoutesCompleted.progress = 0
            tvDistanceCovered.text = "0.0 km"
            tvComplaintsResolved.text = "0"
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

            findViewById<android.view.View>(R.id.btn_notifications).setOnClickListener {
                showNotificationModal()
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

            findViewById<android.view.View>(R.id.row_driver_issues).setOnClickListener {
                val intent = Intent(this, ComplaintsActivity::class.java)
                intent.putExtra("SELECTED_TAB", 1)
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
        }

        override fun onResume() {
            super.onResume()
            complaintsHandler.post(complaintsRunnable)
            setupRealtimeMetrics()
        }

        override fun onPause() {
            super.onPause()
            complaintsHandler.removeCallbacks(complaintsRunnable)
            removeRealtimeMetrics()
        }

        private fun setupRealtimeMetrics() {
            // 0. Notification Listener
            if (sessionManager.isAppNotificationsEnabled()) {
                notificationListener = database.getReference("notifications").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        notificationList.clear()
                        var unreadCount = 0
                        for (child in snapshot.children) {
                            val notification = child.getValue(com.example.myapplication.models.SystemNotification::class.java)
                            if (notification != null) {
                                notificationList.add(notification.copy(id = child.key ?: ""))
                                if (!notification.isRead) unreadCount++
                            }
                        }
                        notificationList.sortByDescending { it.timestamp }
                        
                        if (unreadCount > 0) {
                            badgeNotifications.text = unreadCount.toString()
                            badgeNotifications.visibility = View.VISIBLE
                        } else {
                            badgeNotifications.visibility = View.GONE
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }

            // 1. Fleet Status & Metrics Listener (Consolidated)
            truckListener = database.getReference("truck_locations").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tvActiveTrucks.text = snapshot.childrenCount.toString()
                    
                    val trucks = mutableListOf<TruckLocation>()
                    var totalDist = 0.0
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val todayDate = sdf.format(Date())

                    for (truckSnapshot in snapshot.children) {
                        try {
                            val truckId = truckSnapshot.child("truckId").getValue(String::class.java) ?: "Unknown"
                            val driverName = truckSnapshot.child("driverName").getValue(String::class.java) ?: "Unknown"
                            val lat = truckSnapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                            val lng = truckSnapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                            val speed = truckSnapshot.child("speed").getValue(Double::class.java) ?: 0.0
                            val status = truckSnapshot.child("status").getValue(String::class.java) ?: "offline"
                            val isFull = truckSnapshot.child("isFull").getValue(Boolean::class.java) ?: false

                            val truck = TruckLocation(
                                id = 0,
                                driverId = 0,
                                truckId = truckId,
                                latitude = lat,
                                longitude = lng,
                                speed = speed,
                                status = if (isFull) "full" else status,
                                isFull = isFull,
                                plateNumber = null,
                                updatedAt = "",
                                driverName = driverName
                            )
                            trucks.add(truck)

                            // Calculate distance for today
                            val points = mutableListOf<android.location.Location>()
                            for (p in truckSnapshot.child("route_history").children) {
                                val pLat = p.child("lat").getValue(Double::class.java) ?: continue
                                val pLng = p.child("lng").getValue(Double::class.java) ?: continue
                                val timestamp = p.child("timestamp").getValue(Long::class.java) ?: 0L
                                
                                if (timestamp > 0 && sdf.format(Date(timestamp)) == todayDate) {
                                    points.add(android.location.Location("").apply {
                                        latitude = pLat
                                        longitude = pLng
                                    })
                                }
                            }
                            
                            if (points.size >= 2) {
                                for (i in 0 until points.size - 1) {
                                    totalDist += points[i].distanceTo(points[i+1])
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    fleetAdapter.updateTrucks(trucks)
                    tvDistanceCovered.text = String.format(Locale.getDefault(), "%.1f km", totalDist / 1000.0)
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            // 2. Residents Count Listener
            residentListener = database.getReference("resident_locations").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tvResidentsCount.text = snapshot.childrenCount.toString()
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            // 3. Coverage % Listener
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            coverageListener = database.getReference("collection_logs").orderByChild("date").equalTo(today)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val visitedZones = mutableSetOf<String>()
                        for (log in snapshot.children) {
                            val zoneName = log.child("zoneName").getValue(String::class.java)
                            if (zoneName != null) visitedZones.add(zoneName)
                        }
                        
                        val totalZones = 12 // As defined in PurokZones
                        val percentage = (visitedZones.size.toDouble() / totalZones * 100).toInt()
                        
                        tvCoveragePercent.text = "$percentage%"
                        tvRoutesCompleted.text = "${visitedZones.size} / $totalZones"
                        progressRoutesCompleted.progress = percentage
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }

        private fun removeRealtimeMetrics() {
            truckListener?.let { database.getReference("truck_locations").removeEventListener(it) }
            residentListener?.let { database.getReference("resident_locations").removeEventListener(it) }
            coverageListener?.let { database.getReference("collection_logs").removeEventListener(it) }
            notificationListener?.let { database.getReference("notifications").removeEventListener(it) }
        }

        private fun showNotificationModal() {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notifications, null)
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val rvNotifications = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_notifications)
            val llEmpty = dialogView.findViewById<LinearLayout>(R.id.ll_empty_state)
            val btnClearAll = dialogView.findViewById<TextView>(R.id.btn_clear_all)
            val btnClose = dialogView.findViewById<View>(R.id.btn_close)

            if (notificationList.isEmpty()) {
                rvNotifications.visibility = View.GONE
                llEmpty.visibility = View.VISIBLE
            } else {
                rvNotifications.visibility = View.VISIBLE
                llEmpty.visibility = View.GONE
                
                rvNotifications.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
                val adapter = com.example.myapplication.adapters.NotificationAdapter(this, notificationList) { notification ->
                    // Mark as read
                    database.getReference("notifications").child(notification.id).child("isRead").setValue(true)
                    
                    // Navigate
                    when (notification.type) {
                        "COMPLAINT" -> startActivity(Intent(this, ComplaintsActivity::class.java))
                        "REGISTRATION" -> {
                            val intent = Intent(this, UserManagementActivity::class.java)
                            intent.putExtra("TAB_INDEX", 2)
                            startActivity(intent)
                        }
                        "DRIVER_ISSUE" -> {
                            val intent = Intent(this, ComplaintsActivity::class.java)
                            intent.putExtra("SELECTED_TAB", 1)
                            startActivity(intent)
                        }
                    }
                    dialog.dismiss()
                }
                rvNotifications.adapter = adapter
            }

            btnClearAll.setOnClickListener {
                database.getReference("notifications").removeValue()
                dialog.dismiss()
                Toast.makeText(this, "Notifications cleared", Toast.LENGTH_SHORT).show()
            }

            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }

        private fun fetchComplaintsCount() {
            RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
                override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val complaints = response.body()?.data ?: emptyList()
                        
                        // 1. Pending Complaints (Red badge and metric)
                        val pendingCount = complaints.count { it.status.lowercase() == "pending" }
                        tvComplaintsCount.text = pendingCount.toString()
                        badgeComplaints.text = pendingCount.toString()
                        badgeComplaints.visibility = if (pendingCount > 0) View.VISIBLE else View.GONE
                        
                        // 2. Resolved Today (Summary)
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val resolvedToday = complaints.count { 
                            it.status.lowercase() == "resolved" && 
                            (it.updatedAt?.startsWith(today) == true || it.createdAt.startsWith(today))
                        }
                        tvComplaintsResolved.text = resolvedToday.toString()
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
