package com.example.myapplication.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class GpsStatusMonitor(
    private val context: Context,
    private val onGpsStatusChanged: (isEnabled: Boolean) -> Unit
) : DefaultLifecycleObserver {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var isFirstCheck = true

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkGpsStatus()
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(gpsReceiver, filter)
        checkGpsStatus()
    }

    override fun onPause(owner: LifecycleOwner) {
        context.unregisterReceiver(gpsReceiver)
    }

    private fun checkGpsStatus() {
        val isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        onGpsStatusChanged(isEnabled)

        if (!isEnabled) {
            Toast.makeText(
                context,
                "⚠️ Location/GPS has been turned OFF. Please enable it again to continue using live tracking and map features",
                Toast.LENGTH_LONG
            ).show()
            
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
}
