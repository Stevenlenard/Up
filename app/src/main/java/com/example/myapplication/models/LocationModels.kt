package com.example.myapplication.models

import com.google.gson.annotations.SerializedName

data class TruckLocation(
    val id: Int,
    @SerializedName("driver_id") val driverId: Int,
    @SerializedName("truck_id") val truckId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Double,
    val status: String, // active, idle, offline
    @SerializedName("is_full") val isFull: Boolean,
    @SerializedName("plate_number") val plateNumber: String?,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("driver_name") val driverName: String?,
    @SerializedName("route_points") val routePoints: List<PointCoord>? = null
)

data class PointCoord(
    val latitude: Double,
    val longitude: Double
)

data class LocationsResponse(
    val success: Boolean,
    val message: String?,
    val data: List<TruckLocation>?
)

data class ResidentLocation(
    val userId: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val purok: String,
    val updatedAt: String
)



