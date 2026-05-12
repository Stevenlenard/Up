package com.example.myapplication.fragments

import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.R
import com.example.myapplication.utils.CustomNotification
import com.example.myapplication.utils.SessionManager
import com.google.firebase.database.*
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.locationcomponent.location

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapboxFragment : Fragment() {

    companion object {
        const val MODE_DASHBOARD = "dashboard"
        const val MODE_FULL = "full"
        private const val ARG_MODE = "map_mode"

        fun newInstance(mode: String): MapboxFragment {
            val fragment = MapboxFragment()
            val args = Bundle()
            args.putString(ARG_MODE, mode)
            fragment.arguments = args
            return fragment
        }
    }

    private var mapView: MapView? = null
    private var truckAnnotationManager: PointAnnotationManager? = null
    private var residentAnnotationManager: PointAnnotationManager? = null
    private var userAnnotationManager: PointAnnotationManager? = null
    private var polylineAnnotationManager: PolylineAnnotationManager? = null
    private var userLocationAnnotation: PointAnnotation? = null

    private var truckAnnotations = mutableMapOf<String, PointAnnotation>()
    private var residentAnnotations = mutableMapOf<String, PointAnnotation>()
    private var truckPolylines = mutableMapOf<String, PolylineAnnotation>()
    private var truckRoutePoints = mutableMapOf<String, MutableList<Point>>()

    private var mapMode: String = MODE_DASHBOARD
    private lateinit var sessionManager: SessionManager
    private var firebaseDatabase: DatabaseReference? = null
    private var truckListener: ValueEventListener? = null

    private var currentTrucks: List<com.example.myapplication.models.TruckLocation> = emptyList()
    private var isStyleLoaded = false

    // ✅ FEATURE 1: GEOFENCING RULES — INVISIBLE ZONES
    data class PurokZone(val name: String, val latitude: Double, val longitude: Double, val radiusMeters: Double)
    private val purokZones = listOf(
        PurokZone("Purok 2", 13.9402, 121.1638, 220.0),
        PurokZone("Purok 3", 13.9375, 121.1660, 230.0),
        PurokZone("Purok 4", 13.9430, 121.1625, 250.0),
        PurokZone("Dos Riles", 13.9358, 121.1595, 200.0),
        PurokZone("Sentro", 13.9388, 121.1645, 180.0),
        PurokZone("San Isidro", 13.9342, 121.1620, 210.0),
        PurokZone("Paraiso", 13.9325, 121.1602, 200.0),
        PurokZone("Riverside", 13.9365, 121.1678, 240.0),
        PurokZone("Kalaw Street", 13.9395, 121.1580, 150.0),
        PurokZone("Home Subdivision", 13.9415, 121.1565, 260.0),
        PurokZone("Tanco Road / Ayala Highway", 13.9312, 121.1705, 300.0),
        PurokZone("Brixton Area", 13.9382, 121.1552, 230.0)
    )

    private val notifiedZones = mutableSetOf<String>()
    private val etaNotifiedZones = mutableSetOf<String>()
    private var lastInsidePurok: String? = null
    private var hasCollectedFromAtLeastOneZone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(requireContext())
        mapMode = arguments?.getString(ARG_MODE) ?: MODE_DASHBOARD
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_mapbox, container, false)
        mapView = view.findViewById(R.id.mapView)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
    }

    private fun setupMap() {
        val mapboxMap = mapView?.mapboxMap ?: return

        // ✅ EXACT COORDINATES FROM YOUR SCREENSHOT
        val balintawakPoint = Point.fromLngLat(
            121.158888,   // Longitude: exact from your image
            13.955805     // Latitude: exact from your image
        )

        val defaultCamera = CameraOptions.Builder()
            .center(balintawakPoint)
            .zoom(13.0)    // Perfect zoom to match the view and area size
            .pitch(0.0)
            .bearing(0.0)
            .build()

        mapboxMap.setCamera(defaultCamera)

        mapboxMap.loadStyle(Style.OUTDOORS) { style ->
            isStyleLoaded = true
            loadMarkerIcons(style)
            setupAnnotationManagers()
            enableUserLocationComponent()
            setupFirebaseSync()

            // If we have pending data, apply it now
            if (currentTrucks.isNotEmpty()) updateTrucks(currentTrucks)

            mapboxMap.setCamera(defaultCamera)
        }
    }

    private fun loadMarkerIcons(style: Style) {
        val ctx = context ?: return
        try {
            val locationDrawable = ContextCompat.getDrawable(ctx, R.drawable.ic_location)
            if (locationDrawable != null) {
                // OWN LOCATION (Blue - standardized)
                val bluePin = drawableToBitmap(locationDrawable, Color.parseColor("#2196F3"))
                style.addImage("own-location-blue", bluePin)

                // DRIVER MARKER (Red - for others to see)
                val redPin = drawableToBitmap(locationDrawable, Color.parseColor("#F44336"))
                style.addImage("driver-pin-red", redPin)
            }
        } catch (e: Exception) {
            Log.e("MapboxFragment", "Error loading icons: ${e.message}")
        }
    }

    private fun drawableToBitmap(drawable: Drawable, tintColor: Int? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: 64,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 64,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        tintColor?.let { drawable.setTint(it) }
        drawable.draw(canvas)
        return bitmap
    }

    private fun setupAnnotationManagers() {
        mapView?.let {
            truckAnnotationManager = it.annotations.createPointAnnotationManager()
            residentAnnotationManager = it.annotations.createPointAnnotationManager()
            userAnnotationManager = it.annotations.createPointAnnotationManager()
            polylineAnnotationManager = it.annotations.createPolylineAnnotationManager()
        }
    }

    private fun enableUserLocationComponent() {
        if (ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationComponentPlugin = mapView?.location
        locationComponentPlugin?.updateSettings {
            enabled = true
            pulsingEnabled = true
        }
        locationComponentPlugin?.addOnIndicatorPositionChangedListener(object : OnIndicatorPositionChangedListener {
            override fun onIndicatorPositionChanged(point: Point) {
                updateUserMarker(point)
            }
        })
    }

    private fun updateUserMarker(point: Point) {
        val user = sessionManager.getUser()
        val name = user?.name ?: "Me"
        
        // Own location is always blue as per your preference
        val iconImage = "own-location-blue"

        val annotation = userLocationAnnotation
        if (annotation == null) {
            val options = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(iconImage)
                .withIconSize(1.2)
                .withTextField("$name (You)")
                .withTextColor(Color.BLUE)
                .withTextSize(10.0)
                .withTextOffset(listOf(0.0, 1.2))
            userLocationAnnotation = userAnnotationManager?.create(options)
        } else {
            annotation.point = point
            annotation.iconImage = iconImage
            userAnnotationManager?.update(annotation)
        }
    }

    private var residentListener: ValueEventListener? = null
    private var firebaseResidentRef: DatabaseReference? = null

    private fun setupFirebaseSync() {
        if (firebaseDatabase != null) return // Already syncing

        val user = sessionManager.getUser()
        val role = user?.role?.lowercase() ?: ""

        Log.d("MapboxFragment", "Starting Firebase Sync. Role: $role")
        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"

        // ADMIN & RESIDENT: See trucks
        if (role == "admin" || role == "resident") {
            firebaseDatabase = FirebaseDatabase.getInstance(dbUrl).getReference("truck_locations")
            truckListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        Log.d("MapboxFragment", "Firebase Truck Data Received. Count: ${snapshot.childrenCount}")
                        val trucks = mutableListOf<com.example.myapplication.models.TruckLocation>()
                        val newRoutePoints = mutableMapOf<String, MutableList<Point>>()

                        for (truckSnapshot in snapshot.children) {
                            try {
                                val lat = truckSnapshot.child("latitude").getValue(Double::class.java) ?: continue
                                val lng = truckSnapshot.child("longitude").getValue(Double::class.java) ?: continue
                                val truckId = truckSnapshot.child("truckId").getValue(String::class.java) ?: "GT-001"

                                val speed = truckSnapshot.child("speed").getValue(Double::class.java) ?: 0.0
                                val isFull = truckSnapshot.child("isFull").getValue(Boolean::class.java) ?: false
                                val driverName = truckSnapshot.child("driverName").getValue(String::class.java) ?: "Unknown Driver"

                                // Fetch and Store Full Route History (Trails)
                                val historySnapshot = truckSnapshot.child("route_history")
                                val routePoints = mutableListOf<Point>()
                                for (pointSnapshot in historySnapshot.children) {
                                    val pLat = pointSnapshot.child("lat").getValue(Double::class.java) ?: continue
                                    val pLng = pointSnapshot.child("lng").getValue(Double::class.java) ?: continue
                                    routePoints.add(Point.fromLngLat(pLng, pLat))
                                }
                                newRoutePoints[truckId] = routePoints

                                trucks.add(com.example.myapplication.models.TruckLocation(
                                    id = 0, driverId = 0, truckId = truckId,
                                    latitude = lat, longitude = lng, speed = speed,
                                    status = "active", isFull = isFull,
                                    plateNumber = null, updatedAt = "", driverName = driverName
                                ))
                            } catch (e: Exception) {
                                Log.e("MapboxFragment", "Error parsing truck: ${e.message}")
                            }
                        }

                        withContext(Dispatchers.Main) {
                            truckRoutePoints.clear()
                            truckRoutePoints.putAll(newRoutePoints)
                            updateTrucks(trucks)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("MapboxFragment", "Firebase Truck Sync Cancelled: ${error.message}")
                }
            }
            firebaseDatabase?.addValueEventListener(truckListener!!)
        }


    }

    data class ResidentLocation(val id: String, val name: String, val lat: Double, val lng: Double)

    private fun updateResidents(residents: List<ResidentLocation>) {
        if (!isStyleLoaded || residentAnnotationManager == null) return

        val currentIds = residents.map { it.id }.toSet()
        val idsToRemove = residentAnnotations.keys.filter { it !in currentIds }
        idsToRemove.forEach { id ->
            residentAnnotations[id]?.let { residentAnnotationManager?.delete(it) }
            residentAnnotations.remove(id)
        }

        residents.forEach { res ->
            val point = Point.fromLngLat(res.lng, res.lat)
            val existing = residentAnnotations[res.id]
            if (existing == null) {
                val options = PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage("resident-pin-green")
                    .withIconSize(1.0)
                    .withTextField("RESIDENT: ${res.name}")
                    .withTextColor(Color.parseColor("#2E7D32"))
                    .withTextSize(10.0)
                    .withTextHaloColor(Color.WHITE)
                    .withTextHaloWidth(1.0)
                    .withTextOffset(listOf(0.0, -1.5))
                residentAnnotationManager?.create(options)?.let {
                    residentAnnotations[res.id] = it
                }
            } else {
                existing.point = point
                residentAnnotationManager?.update(existing)
            }
        }
    }

    fun updateTrucks(trucks: List<com.example.myapplication.models.TruckLocation>, autoCenterOnFirst: Boolean = false) {
        val role = sessionManager.getUser()?.role?.lowercase() ?: ""
        if (role == "driver") return // Drivers must NOT see other drivers or trails

        currentTrucks = trucks

        if (!isStyleLoaded || truckAnnotationManager == null) {
            Log.w("MapboxFragment", "Delaying updateTrucks: Style not loaded yet")
            return
        }

        Log.d("MapboxFragment", "Updating Map with ${trucks.size} trucks")

        // 1. Remove markers/polylines for trucks no longer present
        val currentIds = trucks.map { it.truckId }.toSet()
        val idsToRemove = truckAnnotations.keys.filter { it !in currentIds }
        idsToRemove.forEach { id ->
            truckAnnotations[id]?.let { truckAnnotationManager?.delete(it) }
            truckAnnotations.remove(id)
            truckPolylines[id]?.let { polylineAnnotationManager?.delete(it) }
            truckPolylines.remove(id)
        }

        // 2. Update or Create markers and polylines
        trucks.forEach { truck ->
            val currentPoint = Point.fromLngLat(truck.longitude, truck.latitude)

            // Marker Update - Red for all drivers seen by others
            val existingAnnotation = truckAnnotations[truck.truckId]
            if (existingAnnotation == null) {
                val truckOptions = PointAnnotationOptions()
                    .withPoint(currentPoint)
                    .withIconImage("driver-pin-red")
                    .withIconSize(1.2)
                    .withIconAnchor(IconAnchor.BOTTOM)
                    .withTextField("${truck.driverName ?: "Driver"}\n(Truck ${truck.truckId})")
                    .withTextColor(Color.RED)
                    .withTextSize(11.0)
                    .withTextHaloColor(Color.WHITE)
                    .withTextHaloWidth(1.5)
                    .withTextOffset(listOf(0.0, -1.5))
                truckAnnotationManager?.create(truckOptions)?.let {
                    truckAnnotations[truck.truckId] = it
                }
            } else {
                existingAnnotation.point = currentPoint
                existingAnnotation.iconImage = "driver-pin-red"
                truckAnnotationManager?.update(existingAnnotation)
            }

            // Polyline (Trail) Update - Red for consistency
            val points = truckRoutePoints[truck.truckId] ?: mutableListOf()
            if (points.size >= 2) {
                val existingPolyline = truckPolylines[truck.truckId]
                if (existingPolyline == null) {
                    val lineOptions = PolylineAnnotationOptions()
                        .withPoints(points)
                        .withLineColor("#F44336") // Material Red
                        .withLineWidth(4.0)
                        .withLineJoin(LineJoin.ROUND)
                        .withLineOpacity(0.6)
                    polylineAnnotationManager?.create(lineOptions)?.let {
                        truckPolylines[truck.truckId] = it
                    }
                } else {
                    existingPolyline.points = points
                    existingPolyline.lineColorString = "#F44336"
                    polylineAnnotationManager?.update(existingPolyline)
                }
            }

            checkGeofenceNotifications(truck)
        }
    }

    private fun checkGeofenceNotifications(truck: com.example.myapplication.models.TruckLocation) {
        if (hasCollectedFromAtLeastOneZone && truck.isFull) return

        val truckLoc = Location("").apply {
            latitude = truck.latitude
            longitude = truck.longitude
        }

        var currentlyInside: String? = null
        for (zone in purokZones) {
            val zoneLoc = Location("").apply {
                latitude = zone.latitude
                longitude = zone.longitude
            }
            val distance = truckLoc.distanceTo(zoneLoc)

            if (distance <= zone.radiusMeters) {
                currentlyInside = zone.name
                if (zone.name != lastInsidePurok && !notifiedZones.contains(zone.name)) {
                    val message = "🚛 Garbage Truck Update: The truck is now entering ${zone.name}. Please prepare your trash!"
                    activity?.let { CustomNotification.showTopNotification(it, message, false) }
                    sendSMSAlert(message)
                    notifiedZones.add(zone.name)
                    hasCollectedFromAtLeastOneZone = true
                }
            } else {
                if (truck.speed > 1.0) {
                    val etaSeconds = distance / (truck.speed / 3.6)
                    if (etaSeconds <= 900 && !etaNotifiedZones.contains(zone.name)) {
                        val message = "⏳ Garbage Truck Update: The truck is estimated to arrive in ${zone.name} in about 15 minutes. Please bring out your trash now!"
                        activity?.let { CustomNotification.showTopNotification(it, message, false) }
                        sendSMSAlert(message)
                        etaNotifiedZones.add(zone.name)
                    }
                }
            }
        }
        lastInsidePurok = currentlyInside
    }

    private fun sendSMSAlert(message: String) {
        Log.d("SMS_ALERT", "Sending SMS: $message")
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        // ✅ SAME EXACT COORDINATES HERE — ALWAYS CENTERED
        val balintawakPoint = Point.fromLngLat(
            121.158888,
            13.955805
        )
        val cameraOptions = CameraOptions.Builder()
            .center(balintawakPoint)
            .zoom(13.0)
            .pitch(0.0)
            .bearing(0.0)
            .build()

        mapView?.mapboxMap?.setCamera(cameraOptions)

        mapView?.postDelayed({
            mapView?.mapboxMap?.setCamera(cameraOptions)
        }, 500)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroyView() {
        truckListener?.let { firebaseDatabase?.removeEventListener(it) }
        residentListener?.let { firebaseResidentRef?.removeEventListener(it) }
        mapView?.onDestroy()
        mapView = null
        super.onDestroyView()
    }

    fun clearMap() {
        truckAnnotationManager?.deleteAll()
        residentAnnotationManager?.deleteAll()
        userAnnotationManager?.deleteAll()
        polylineAnnotationManager?.deleteAll()
        truckAnnotations.clear()
        residentAnnotations.clear()
        truckPolylines.clear()
        truckRoutePoints.clear()
        notifiedZones.clear()
        etaNotifiedZones.clear()
        lastInsidePurok = null
        hasCollectedFromAtLeastOneZone = false
    }
}