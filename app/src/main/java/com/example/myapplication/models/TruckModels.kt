package com.example.myapplication.models

data class Truck(
    var truckId: String,
    var plateNumber: String,
    var driverName: String,
    var status: String, // "Active", "Inactive", "Maintenance"
    var fuelLevel: Int = 100,
    var isActive: Boolean = true
)
