package com.example.myapplication.models

data class SystemNotification(
    var id: String = "",
    val type: String = "", // COMPLAINT, REGISTRATION, DRIVER_ISSUE
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val relatedId: String = "", // ID of the complaint, user, or report
    val status: String = "PENDING",
    val adminResponse: String? = null
)

data class DriverIssue(
    val id: String = "",
    val driverName: String = "",
    val truckId: String = "",
    val issueType: String = "",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING" // PENDING, RESOLVED
)
