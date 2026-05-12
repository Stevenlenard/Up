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
import com.example.myapplication.utils.SessionManager
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LocationUpdateService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sessionManager: SessionManager

    private val NOTIFICATION_ID = 12345
    private val CHANNEL_ID = "location_service_channel"

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLiveLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(2000)
            .setMinUpdateDistanceMeters(5f)
            .setMaxUpdateDelayMillis(4000)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun updateLiveLocation(location: Location) {
        val user = sessionManager.getUser() ?: return
        val timestamp = System.currentTimeMillis().toString()
        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"

        if (user.role.lowercase() == "driver") {
            val truckId = user.preferredTruck ?: "GT-001"
            val isFull = false 

            val database = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations")
            
            val locationData = mapOf(
                "truckId" to truckId,
                "driverId" to user.userId,
                "driverName" to user.name,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "speed" to location.speed.toDouble(),
                "isFull" to isFull,
                "status" to "active",
                "updatedAt" to timestamp
            )

            // Update current position
            database.child(truckId).setValue(locationData)
                .addOnSuccessListener {
                    Log.d("LocationService", "Firebase Truck Update Success: $truckId")
                }
                .addOnFailureListener {
                    Log.e("LocationService", "Firebase Truck Update Failed: ${it.message}")
                }

            // Save full history
            val pointData = mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "time" to timestamp
            )
            database.child(truckId).child("route_history").push().setValue(pointData)

            // Retrofit fallback
            RetrofitClient.instance.updateLocation(
                user.userId, location.latitude, location.longitude, 
                truckId, location.speed.toDouble(), isFull
            ).enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {}
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {}
            })
        } else {
            // ✅ SAVING RESIDENT LOCATION
            val database = FirebaseDatabase.getInstance(dbUrl).getReference("resident_locations")
            val locationData = mapOf(
                "userId" to user.userId,
                "name" to user.name,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "purok" to (user.purok ?: "Unknown"),
                "updatedAt" to timestamp
            )
            database.child(user.userId.toString()).setValue(locationData)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Garbage Tracker")
            .setContentText("Transmitting GPS location...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(0xFF2196F3.toInt())
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
