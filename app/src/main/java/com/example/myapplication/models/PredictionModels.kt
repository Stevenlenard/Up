package com.example.myapplication.models

data class PurokPrediction(
    val name: String,
    val eta: Long = 0L,
    val distanceMeters: Double = 0.0,
    val status: String = "incoming",
    val predictedVolumeKg: Double = 0.0
)

data class WasteInsight(
    val title: String,
    val description: String,
    val severity: String = "info" // info, warning, critical
)

data class HistoricalLog(
    val date: String = "",
    val purokName: String = "",
    val weightKg: Double = 0.0,
    val durationMinutes: Int = 0
)
