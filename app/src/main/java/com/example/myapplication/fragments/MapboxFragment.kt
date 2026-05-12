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

    interface OnTrucksUpdatedListener {
        fun onTrucksUpdated(trucks: List<com.example.myapplication.models.TruckLocation>)
    }

    private var trucksUpdatedListener: OnTrucksUpdatedListener? = null

    fun setOnTrucksUpdatedListener(listener: OnTrucksUpdatedListener) {
        trucksUpdatedListener = listener
    }

    private var mapView: MapView? = null
    private var truckAnnotationManager: PointAnnotationManager? = null
    private var residentAnnotationManager: PointAnnotationManager? = null
    private var userAnnotationManager: PointAnnotationManager? = null
    private var polylineAnnotationManager: PolylineAnnotationManager? = null
    private var userLocationAnnotation: PointAnnotation? = null

    private var truckAnnotations = mutableMapOf<String, PointAnnotation>()
    private var residentAnnotations = mutableMapOf<String, PointAnnotation>()
    private var truckPolylines = mutableMapOf<String, MutableList<PolylineAnnotation>>()
    private var truckRoutePoints = mutableMapOf<String, MutableList<RoutePoint>>()
    private var optimizedPolylines = mutableMapOf<String, PolylineAnnotation>()

    data class RoutePoint(val point: Point, val speed: Double, val timestamp: Long = 0)
    data class AnalyticsData(val distanceKm: Double, val fuelLiters: Double, val stops: Int)
    data class CoverageItem(val name: String, val isCompleted: Boolean, val completionTime: String?)

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

    fun getPurokZones() = purokZones

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
        val balintawakPoint = Point.fromLngLat(121.158888, 13.955805)
        val defaultCamera = CameraOptions.Builder().center(balintawakPoint).zoom(13.0).pitch(0.0).bearing(0.0).build()
        mapboxMap.setCamera(defaultCamera)
        mapboxMap.loadStyle(Style.OUTDOORS) { style ->
            isStyleLoaded = true
            loadMarkerIcons(style)
            setupAnnotationManagers()
            enableUserLocationComponent()
            setupFirebaseSync()
            if (currentTrucks.isNotEmpty()) updateTrucks(currentTrucks)
            mapboxMap.setCamera(defaultCamera)
        }
    }

    private fun loadMarkerIcons(style: Style) {
        val ctx = context ?: return
        try {
            val locationDrawable = ContextCompat.getDrawable(ctx, R.drawable.ic_location)
            if (locationDrawable != null) {
                style.addImage("own-location-blue", drawableToBitmap(locationDrawable, Color.parseColor("#2196F3")))
                style.addImage("driver-pin-red", drawableToBitmap(locationDrawable, Color.parseColor("#F44336")))
                style.addImage("driver-pin-green", drawableToBitmap(locationDrawable, Color.parseColor("#4CAF50")))
                style.addImage("driver-pin-yellow", drawableToBitmap(locationDrawable, Color.parseColor("#FFC107")))
                style.addImage("driver-pin-blue", drawableToBitmap(locationDrawable, Color.parseColor("#2196F3")))
            }
        } catch (e: Exception) { Log.e("MapboxFragment", "Error loading icons: ${e.message}") }
    }

    private fun drawableToBitmap(drawable: Drawable, tintColor: Int? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth.takeIf { it > 0 } ?: 64, drawable.intrinsicHeight.takeIf { it > 0 } ?: 64, Bitmap.Config.ARGB_8888)
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
        if (ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val locationComponentPlugin = mapView?.location
        locationComponentPlugin?.updateSettings { enabled = true; pulsingEnabled = true }
        locationComponentPlugin?.addOnIndicatorPositionChangedListener(object : OnIndicatorPositionChangedListener {
            override fun onIndicatorPositionChanged(point: Point) { updateUserMarker(point) }
        })
    }

    private fun updateUserMarker(point: Point) {
        val name = sessionManager.getUser()?.name ?: "Me"
        val annotation = userLocationAnnotation
        if (annotation == null) {
            val options = PointAnnotationOptions().withPoint(point).withIconImage("own-location-blue").withIconSize(1.2).withTextField("$name (You)").withTextColor(Color.BLUE).withTextSize(10.0).withTextOffset(listOf(0.0, 1.2))
            userLocationAnnotation = userAnnotationManager?.create(options)
        } else {
            annotation.point = point
            userAnnotationManager?.update(annotation)
        }
    }

    private fun setupFirebaseSync() {
        if (firebaseDatabase != null) return
        val role = sessionManager.getUser()?.role?.lowercase() ?: ""
        if (role == "admin" || role == "resident") {
            firebaseDatabase = FirebaseDatabase.getInstance("https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("truck_locations")
            truckListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        val trucks = mutableListOf<com.example.myapplication.models.TruckLocation>()
                        val newRoutePoints = mutableMapOf<String, MutableList<RoutePoint>>()
                        for (truckSnapshot in snapshot.children) {
                            try {
                                val lat = truckSnapshot.child("latitude").getValue(Double::class.java) ?: continue
                                val lng = truckSnapshot.child("longitude").getValue(Double::class.java) ?: continue
                                val truckId = truckSnapshot.child("truckId").getValue(String::class.java) ?: "GT-001"
                                val driverName = truckSnapshot.child("driverName").getValue(String::class.java) ?: "Unknown Driver"
                                val historySnapshot = truckSnapshot.child("route_history")
                                val routePoints = mutableListOf<RoutePoint>()
                                for (pointSnapshot in historySnapshot.children) {
                                    val pLat = pointSnapshot.child("lat").getValue(Double::class.java) ?: continue
                                    val pLng = pointSnapshot.child("lng").getValue(Double::class.java) ?: continue
                                    val pSpeed = pointSnapshot.child("speed").getValue(Double::class.java) ?: 0.0
                                    val pTime = pointSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                                    routePoints.add(RoutePoint(Point.fromLngLat(pLng, pLat), pSpeed, pTime))
                                }
                                newRoutePoints[truckId] = routePoints
                                trucks.add(com.example.myapplication.models.TruckLocation(id = 0, driverId = 0, truckId = truckId, latitude = lat, longitude = lng, speed = truckSnapshot.child("speed").getValue(Double::class.java) ?: 0.0, status = "active", isFull = truckSnapshot.child("isFull").getValue(Boolean::class.java) ?: false, plateNumber = null, updatedAt = "", driverName = driverName))
                            } catch (e: Exception) {}
                        }
                        withContext(Dispatchers.Main) { 
                            truckRoutePoints.clear()
                            truckRoutePoints.putAll(newRoutePoints)
                            updateTrucks(trucks) 
                            trucksUpdatedListener?.onTrucksUpdated(trucks)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            firebaseDatabase?.addValueEventListener(truckListener!!)
        }
    }

    private var selectedTruckId: String? = null

    fun setSelectedTruck(truckId: String?) {
        selectedTruckId = truckId
        // Clear all histories and redraw only selected
        truckPolylines.keys.forEach { id ->
            truckPolylines[id]?.forEach { polylineAnnotationManager?.delete(it) }
        }
        truckPolylines.clear()
        
        if (truckId != null) {
            drawHeatmap(truckId)
        }
    }

    fun updateTrucks(trucks: List<com.example.myapplication.models.TruckLocation>, autoCenterOnFirst: Boolean = false) {
        if (sessionManager.getUser()?.role?.lowercase() == "driver") return
        
        // Optimization: Don't redraw if the data is identical (unless it's the first load)
        if (currentTrucks == trucks && isStyleLoaded) {
            // Even if list is same, update the specific selected truck history if it changed
            selectedTruckId?.let { drawHeatmap(it) }
            return
        }

        currentTrucks = trucks
        if (!isStyleLoaded || truckAnnotationManager == null) return
        val currentIds = trucks.map { it.truckId }.toSet()
        
        truckAnnotations.keys.filter { it !in currentIds }.forEach { id -> 
            truckAnnotations[id]?.let { truckAnnotationManager?.delete(it) }
            truckAnnotations.remove(id)
            truckPolylines[id]?.forEach { polylineAnnotationManager?.delete(it) }
            truckPolylines.remove(id)
            optimizedPolylines[id]?.let { polylineAnnotationManager?.delete(it) }
            optimizedPolylines.remove(id)
        }

        trucks.forEach { truck ->
            if (truck.status == "offline") return@forEach
            val currentPoint = Point.fromLngLat(truck.longitude, truck.latitude)
            val (iconImage, statusColor) = when (truck.status.lowercase()) { 
                "active" -> "driver-pin-green" to Color.parseColor("#4CAF50")
                "idle" -> "driver-pin-yellow" to Color.parseColor("#FFC107")
                "full" -> "driver-pin-red" to Color.parseColor("#F44336")
                "completed" -> "driver-pin-blue" to Color.parseColor("#2196F3")
                else -> "driver-pin-red" to Color.RED 
            }
            
            val existingAnnotation = truckAnnotations[truck.truckId]
            if (existingAnnotation == null) {
                val truckOptions = PointAnnotationOptions()
                    .withPoint(currentPoint)
                    .withIconImage(iconImage)
                    .withIconSize(1.2)
                    .withIconAnchor(IconAnchor.BOTTOM)
                    .withTextField("${truck.driverName ?: "Driver"}\n(${truck.status.uppercase()})")
                    .withTextColor(statusColor)
                    .withTextSize(11.0)
                    .withTextHaloColor(Color.WHITE)
                    .withTextHaloWidth(1.5)
                    .withTextOffset(listOf(0.0, -1.5))
                truckAnnotations[truck.truckId] = truckAnnotationManager!!.create(truckOptions)
            } else { 
                existingAnnotation.point = currentPoint
                existingAnnotation.iconImage = iconImage
                existingAnnotation.textField = "${truck.driverName ?: "Driver"}\n(${truck.status.uppercase()})"
                existingAnnotation.textColorInt = statusColor
                truckAnnotationManager?.update(existingAnnotation) 
            }

            // Only draw history if this is the selected truck
            if (truck.truckId == selectedTruckId) {
                drawHeatmap(truck.truckId)
            }
        }
    }

    private fun drawHeatmap(truckId: String) {
        val points = truckRoutePoints[truckId] ?: return
        if (points.size < 2) return

        // Clear existing polylines for this truck
        truckPolylines[truckId]?.forEach { polylineAnnotationManager?.delete(it) }
        val newPolylines = mutableListOf<PolylineAnnotation>()

        // 🛡️ REFINEMENT: Remove duplicate points in history to prevent line artifacts
        val filteredPoints = mutableListOf<RoutePoint>()
        if (points.isNotEmpty()) {
            filteredPoints.add(points[0])
            for (i in 1 until points.size) {
                val dist = FloatArray(1)
                Location.distanceBetween(
                    points[i].point.latitude(), points[i].point.longitude(),
                    points[i-1].point.latitude(), points[i-1].point.longitude(),
                    dist
                )
                // Only keep points that are at least 1.5 meters apart for drawing
                if (dist[0] > 1.5) {
                    filteredPoints.add(points[i])
                }
            }
        }

        if (filteredPoints.size < 2) return

        // Split points into segments based on speed for heatmap
        var currentSegment = mutableListOf<Point>()
        var currentType = getSpeedType(filteredPoints[0].speed)

        for (i in filteredPoints.indices) {
            val type = getSpeedType(filteredPoints[i].speed)
            if (type != currentType && currentSegment.size >= 2) {
                val polyline = drawSegment(currentSegment, currentType)
                if (polyline != null) newPolylines.add(polyline)
                currentSegment = mutableListOf(filteredPoints[i-1].point, filteredPoints[i].point)
                currentType = type
            } else {
                currentSegment.add(filteredPoints[i].point)
            }
        }
        
        if (currentSegment.size >= 2) {
            val polyline = drawSegment(currentSegment, currentType)
            if (polyline != null) newPolylines.add(polyline)
        }

        truckPolylines[truckId] = newPolylines
    }

    private fun getSpeedType(speed: Double): String {
        return when {
            speed < 5.0 -> "stopped"
            speed < 15.0 -> "slow"
            else -> "moving"
        }
    }

    private fun drawSegment(points: List<Point>, type: String): PolylineAnnotation? {
        val color = when (type) {
            "stopped" -> "#F44336" // Red
            "slow" -> "#FFC107"    // Yellow
            else -> "#4CAF50"      // Green
        }
        
        val options = PolylineAnnotationOptions()
            .withPoints(points)
            .withLineColor(color)
            .withLineWidth(4.0)
            .withLineOpacity(0.7)
            .withLineJoin(LineJoin.ROUND)
            
        return polylineAnnotationManager?.create(options)
    }

    fun toggleOptimization(truckId: String, visible: Boolean) {
        if (!visible) {
            optimizedPolylines[truckId]?.let { polylineAnnotationManager?.delete(it) }
            optimizedPolylines.remove(truckId)
            return
        }

        // Simulate optimization by connecting all purok centers in order
        val optimizedPoints = purokZones.map { Point.fromLngLat(it.longitude, it.latitude) }
        val options = PolylineAnnotationOptions()
            .withPoints(optimizedPoints)
            .withLineColor("#2196F3")
            .withLineWidth(6.0) // Thicker to distinguish
            .withLineOpacity(0.4)
            .withLineJoin(LineJoin.ROUND)
            
        optimizedPolylines[truckId] = polylineAnnotationManager!!.create(options)
    }

    fun getAnalytics(truckId: String): AnalyticsData {
        val points = truckRoutePoints[truckId] ?: return AnalyticsData(0.0, 0.0, 0)
        var totalDist = 0.0
        var stopCount = 0
        
        // Intelligent Stop Detection Logic
        // A 'Stop' is defined as a cluster of points within 10 meters 
        // that spans more than 45 seconds of duration.
        var potentialStopStartTime: Long = 0
        var isCurrentlyStopped = false

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i+1]
            
            val results = FloatArray(1)
            Location.distanceBetween(p1.point.latitude(), p1.point.longitude(), p2.point.latitude(), p2.point.longitude(), results)
            totalDist += results[0]
            
            // If speed is very low (< 1.5 km/h)
            if (p1.speed < 1.5) {
                if (potentialStopStartTime == 0L) {
                    potentialStopStartTime = p1.timestamp
                } else {
                    val duration = p2.timestamp - potentialStopStartTime
                    // If they stay in this low-speed state for > 45 seconds, count as a collection stop
                    if (duration > 45000 && !isCurrentlyStopped) {
                        stopCount++
                        isCurrentlyStopped = true
                    }
                }
            } else {
                // Moving again
                potentialStopStartTime = 0L
                isCurrentlyStopped = false
            }
        }
        
        val km = totalDist / 1000.0
        val fuel = km / 5.0 // Est 5km/L for heavy vehicle
        
        return AnalyticsData(km, fuel, stopCount)
    }

    fun getCoverage(truckId: String): List<CoverageItem> {
        val points = truckRoutePoints[truckId] ?: return purokZones.map { CoverageItem(it.name, false, null) }
        
        return purokZones.map { zone ->
            var visited = false
            var firstVisitedTime: String? = null
            
            for (p in points) {
                val results = FloatArray(1)
                Location.distanceBetween(p.point.latitude(), p.point.longitude(), zone.latitude, zone.longitude, results)
                if (results[0] <= zone.radiusMeters) {
                    visited = true
                    if (p.timestamp > 0) {
                        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                        firstVisitedTime = sdf.format(java.util.Date(p.timestamp))
                    }
                    break
                }
            }
            CoverageItem(zone.name, visited, firstVisitedTime)
        }
    }

    private fun logNotificationToFirebase(truckId: String, zoneName: String, type: String, message: String) {
        val ref = FirebaseDatabase.getInstance("https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("notification_logs")
        val timestamp = System.currentTimeMillis(); val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        ref.push().setValue(mapOf("truckId" to truckId, "zoneName" to zoneName, "type" to type, "message" to message, "timestamp" to timestamp, "date" to date))
    }

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); val balintawakPoint = Point.fromLngLat(121.158888, 13.955805); val cameraOptions = CameraOptions.Builder().center(balintawakPoint).zoom(13.0).pitch(0.0).bearing(0.0).build(); mapView?.mapboxMap?.setCamera(cameraOptions) }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onDestroyView() { truckListener?.let { firebaseDatabase?.removeEventListener(it) }; mapView?.onDestroy(); mapView = null; super.onDestroyView() }

    fun clearMap() {
        truckAnnotationManager?.deleteAll()
        residentAnnotationManager?.deleteAll()
        userAnnotationManager?.deleteAll()
        polylineAnnotationManager?.deleteAll()
        truckAnnotations.clear()
        residentAnnotations.clear()
        truckPolylines.clear()
        truckRoutePoints.clear()
    }
}
