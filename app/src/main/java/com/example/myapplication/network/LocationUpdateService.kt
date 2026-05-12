package com.example.myapplication.network

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.utils.PredictionEngine
import com.example.myapplication.utils.PurokManager
import com.example.myapplication.utils.SessionManager
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import android.media.RingtoneManager
import android.graphics.Color
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class LocationUpdateService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sessionManager: SessionManager
    private var alertListener: ValueEventListener? = null
    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"

    private val NOTIFICATION_ID = 12345
    private val ALERT_NOTIFICATION_ID = 54321
    private val CHANNEL_ID = "location_service_channel"
    private val ALERT_CHANNEL_ID = "garbage_alert_channel"

    private lateinit var notifiedZones: MutableSet<String>
    private lateinit var etaNotifiedZones: MutableSet<String>
    private var lastInsidePurok: String? = null
    private var lastShownAlertTimestamp: Long = 0L

    private var lastLocation: Location? = null
    private var lastSavedLocation: Location? = null
    private val MIN_DISTANCE_METERS = 3.0 // Dead zone: Ignore movements less than 3 meters
    private val SMOOTHING_FACTOR = 0.5 // Lower = more smooth, but more lag
    
    private var isTruckFull = false
    private var isFullListener: ValueEventListener? = null
    private var serviceStartTime = 0L
    private var zonesCoveredThisTrip = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        serviceStartTime = System.currentTimeMillis()
        
        loadNotifiedSets()
        
        val user = sessionManager.getUser()
        if (user?.role?.lowercase() == "driver") {
            val truckId = user.preferredTruck ?: "GT-001"
            isFullListener = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations").child(truckId).child("isFull").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { isTruckFull = snapshot.getValue(Boolean::class.java) ?: false }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location -> lastLocation = location; updateLiveLocation(location); checkAutoFullConditions() }
            }
        }
    }

    private fun loadNotifiedSets() {
        val prefs = getSharedPreferences("location_service_prefs", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString("last_reset_date", "")

        if (lastDate != today) {
            notifiedZones = mutableSetOf()
            etaNotifiedZones = mutableSetOf()
            prefs.edit().putString("last_reset_date", today).apply()
            saveNotifiedSets()
        } else {
            notifiedZones = prefs.getStringSet("notified_zones", emptySet())?.toMutableSet() ?: mutableSetOf()
            etaNotifiedZones = prefs.getStringSet("eta_notified_zones", emptySet())?.toMutableSet() ?: mutableSetOf()
        }
    }

    private fun saveNotifiedSets() {
        val prefs = getSharedPreferences("location_service_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("notified_zones", notifiedZones)
            .putStringSet("eta_notified_zones", etaNotifiedZones)
            .apply()
    }

    private fun checkAutoFullConditions() {
        val user = sessionManager.getUser() ?: return
        if (user.role.lowercase() != "driver" || isTruckFull) return
        val truckId = user.preferredTruck ?: "GT-001"
        if (System.currentTimeMillis() - serviceStartTime > 4 * 60 * 60 * 1000L || zonesCoveredThisTrip.size >= 3) { markTruckAsFull(truckId, "AUTO") }
    }

    private fun markTruckAsFull(truckId: String, reason: String) {
        isTruckFull = true
        val ref = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations").child(truckId)
        ref.child("isFull").setValue(true); ref.child("status").setValue("full")
        
        // Notify current zone that truck is full
        lastInsidePurok?.let { zoneName ->
            FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(zoneName).setValue(mapOf(
                "message" to "Truck is full. Collection for $zoneName will resume after unloading.",
                "type" to "FULL_ALERT",
                "timestamp" to System.currentTimeMillis(),
                "truck_id" to truckId
            ))
        }

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        FirebaseDatabase.getInstance(dbUrl).getReference("collection_logs").push().setValue(mapOf("truckId" to truckId, "timestamp" to System.currentTimeMillis(), "type" to "FULL", "reason" to reason, "date" to today))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION_ID, notification)
        startLocationUpdates()
        setupAlertListener()
        return START_STICKY
    }

    private fun setupAlertListener() {
        if (alertListener != null) return // Already attached
        
        val user = sessionManager.getUser() ?: return
        if (user.role.lowercase() != "resident") return
        val alertRef = FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(user.purok ?: "Purok 2")
        alertListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val message = snapshot.child("message").getValue(String::class.java)
                
                // Show notification only if:
                // 1. It's newer than the last one we showed
                // 2. It happened within the last 10 minutes
                // 3. There is an actual message
                if (timestamp > lastShownAlertTimestamp && System.currentTimeMillis() - timestamp < 600000 && message != null) {
                    lastShownAlertTimestamp = timestamp
                    showSystemNotification("Garbage Truck Alert", message)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        alertRef.addValueEventListener(alertListener!!)
    }

    private fun showSystemNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID).setSmallIcon(R.drawable.ic_truck).setContentTitle(title).setContentText(message).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build()
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return }
        fusedLocationClient.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500).build(), locationCallback, Looper.getMainLooper())
    }

    private fun updateLiveLocation(location: Location) {
        val user = sessionManager.getUser() ?: return
        val timestamp = System.currentTimeMillis()
        
        // --- 🛡️ INTELLIGENT GPS FILTERING ---
        var filteredLocation = location
        
        lastSavedLocation?.let { last ->
            val distance = location.distanceTo(last)
            // 1. DEAD ZONE FILTER: If moved less than 3 meters, ignore (filters out jitter)
            if (distance < MIN_DISTANCE_METERS) {
                Log.d("LocationService", "Filtered: Jitter detected (dist: $distance)")
                return 
            }
            
            // 2. LOW PASS SMOOTHING: Blend with last location to remove spikes
            val smoothedLat = (location.latitude * SMOOTHING_FACTOR) + (last.latitude * (1.0 - SMOOTHING_FACTOR))
            val smoothedLng = (location.longitude * SMOOTHING_FACTOR) + (last.longitude * (1.0 - SMOOTHING_FACTOR))
            
            filteredLocation = Location(location).apply {
                latitude = smoothedLat
                longitude = smoothedLng
            }
        }
        
        lastSavedLocation = filteredLocation
        // --- END FILTERING ---

        if (user.role.lowercase() == "driver") {
            val truckId = user.preferredTruck ?: "GT-001"
            val database = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations")
            database.child(truckId).get().addOnSuccessListener { snapshot ->
                val status = snapshot.child("status").getValue(String::class.java) ?: "active"
                val isFull = snapshot.child("isFull").getValue(Boolean::class.java) ?: false
                database.child(truckId).updateChildren(mapOf(
                    "truckId" to truckId, 
                    "driverId" to user.userId, 
                    "driverName" to user.name, 
                    "latitude" to filteredLocation.latitude, 
                    "longitude" to filteredLocation.longitude, 
                    "speed" to filteredLocation.speed.toDouble(), 
                    "isFull" to isFull, 
                    "status" to status, 
                    "updatedAt" to timestamp
                ))
                if (!isFull && status == "active") checkGeofences(filteredLocation, user.name)
                database.child(truckId).child("route_history").push().setValue(mapOf(
                    "lat" to filteredLocation.latitude, 
                    "lng" to filteredLocation.longitude, 
                    "speed" to filteredLocation.speed.toDouble(),
                    "timestamp" to timestamp
                ))
            }
            RetrofitClient.instance.updateLocation(user.userId, filteredLocation.latitude, filteredLocation.longitude, truckId, filteredLocation.speed.toDouble(), isTruckFull).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {}
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {}
            })
        } else {
            FirebaseDatabase.getInstance(dbUrl).getReference("resident_locations").child(user.userId.toString()).setValue(mapOf(
                "userId" to user.userId, 
                "name" to user.name, 
                "latitude" to filteredLocation.latitude, 
                "longitude" to filteredLocation.longitude, 
                "purok" to (user.purok ?: "Unknown"), 
                "updatedAt" to timestamp
            ))
        }
    }

    private fun checkGeofences(location: Location, driverName: String) {
        val truckId = sessionManager.getUser()?.preferredTruck ?: "GT-001"
        
        // 1. Check for Arrival (Inside Zone)
        val currentZone = PurokManager.getZoneAt(location.latitude, location.longitude)
        val currentlyInside = currentZone?.name
        
        if (currentlyInside != null) {
            zonesCoveredThisTrip.add(currentlyInside)
            if (currentlyInside != lastInsidePurok) {
                logZoneEvent(truckId, currentlyInside, "ENTRY")
                if (!isTruckFull && !notifiedZones.contains(currentlyInside)) {
                    sendPurokAlert(currentlyInside, "The garbage truck has arrived at $currentlyInside. Please bring out your trash!", driverName, truckId, location)
                    notifiedZones.add(currentlyInside)
                    saveNotifiedSets()
                }
            }
        }
        
        if (lastInsidePurok != null && lastInsidePurok != currentlyInside) {
            logZoneEvent(truckId, lastInsidePurok!!, "EXIT")
        }
        lastInsidePurok = currentlyInside

        // 2. Check for Proximity/ETA (Nearby Zones)
        if (!isTruckFull) {
            for (zone in PurokManager.purokZones) {
                if (zone.name == currentlyInside) continue // Already handled arrival
                if (etaNotifiedZones.contains(zone.name)) continue // Already notified ETA today
                
                val zoneLoc = Location("").apply { latitude = zone.latitude; longitude = zone.longitude }
                val distance = location.distanceTo(zoneLoc)
                
                // Only consider if within 5km for ETA accuracy
                if (distance < 5000) {
                    val etaSeconds = PredictionEngine.predictArrivalTime(distance.toDouble(), emptyList())
                    
                    // Trigger "Prep Alert" at ~15 minutes (900 seconds)
                    if (etaSeconds <= 900) {
                        // Smart Filter: Bearing check (is truck moving TOWARDS the zone?)
                        if (location.hasBearing()) {
                            val bearingToZone = location.bearingTo(zoneLoc)
                            val bearingDiff = Math.abs(location.bearing - bearingToZone)
                            // If bearing difference is > 90 degrees, it's moving away
                            if (bearingDiff > 90 && bearingDiff < 270) continue
                        }

                        sendPurokAlert(zone.name, "Garbage truck is about 15 minutes away from ${zone.name}. Please prepare your trash!", driverName, truckId, location)
                        etaNotifiedZones.add(zone.name)
                        saveNotifiedSets()
                    }
                }
            }
        }
    }

    private fun sendPurokAlert(purokName: String, message: String, driver: String, truckId: String, loc: Location) {
        val alertData = mapOf(
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "driver" to driver,
            "truck_id" to truckId,
            "latitude" to loc.latitude,
            "longitude" to loc.longitude,
            "type" to if (message.contains("15 minutes")) "PREP_ALERT" else "ARRIVAL_ALERT"
        )
        FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(purokName).setValue(alertData)
    }

    private fun logZoneEvent(truckId: String, zoneName: String, type: String) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        FirebaseDatabase.getInstance(dbUrl).getReference("collection_logs").push().setValue(mapOf("truckId" to truckId, "zoneName" to zoneName, "timestamp" to System.currentTimeMillis(), "type" to type, "date" to today))
        RetrofitClient.instance.logCollection(truckId, zoneName, type).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {}
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {}
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN))
            manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL_ID, "Garbage Alerts", NotificationManager.IMPORTANCE_HIGH).apply { enableLights(true); lightColor = Color.GREEN; enableVibration(true) })
        }
    }

    private fun createNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Waste Management").setContentText("Service is active").setSmallIcon(R.drawable.ic_truck).setPriority(NotificationCompat.PRIORITY_MIN).build()
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { 
        fusedLocationClient.removeLocationUpdates(locationCallback)
        alertListener?.let {
            val user = sessionManager.getUser()
            val purok = user?.purok ?: "Purok 2"
            FirebaseDatabase.getInstance(dbUrl).getReference("alerts").child(purok).removeEventListener(it)
        }
        isFullListener?.let {
            val user = sessionManager.getUser()
            val truckId = user?.preferredTruck ?: "GT-001"
            FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations").child(truckId).child("isFull").removeEventListener(it)
        }
        super.onDestroy() 
    }
}
