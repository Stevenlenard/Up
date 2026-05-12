package com.example.myapplication.utils

import kotlin.math.pow

object PredictionEngine {

    /**
     * 🧠 MACHINE LEARNING: Simple Linear Regression for Arrival Time
     * Y = mX + b (Time = Speed_Inverse * Distance + Baseline_Delay)
     * Handles Time-Series forecasting for truck arrival.
     */
    fun predictArrivalTime(distanceMeters: Double, historicalData: List<Pair<Double, Double>>): Double {
        if (historicalData.size < 3) {
            // Fallback: Municipality standard 20 km/h average (5.5 m/s)
            return distanceMeters / 5.5
        }

        val n = historicalData.size
        val sumX = historicalData.sumOf { it.first }
        val sumY = historicalData.sumOf { it.second }
        val sumXY = historicalData.sumOf { it.first * it.second }
        val sumX2 = historicalData.sumOf { it.first.pow(2) }

        val denominator = n * sumX2 - sumX.pow(2)
        if (denominator == 0.0) return distanceMeters / 5.5

        val m = (n * sumXY - sumX * sumY) / denominator
        val b = (sumY - m * sumX) / n

        val prediction = (m * distanceMeters) + b
        
        // Safety: Ensure prediction isn't physically impossible (e.g. > 100km/h)
        return if (prediction < (distanceMeters / 27.0)) distanceMeters / 5.5 else prediction
    }

    // --- GEOSPATIAL WASTE PREDICTION ---

    private val purokZones = mapOf(
        "Purok 2" to 220.0, "Purok 3" to 230.0, "Purok 4" to 250.0,
        "Dos Riles" to 200.0, "Sentro" to 180.0, "San Isidro" to 210.0,
        "Paraiso" to 200.0, "Riverside" to 240.0, "Kalaw Street" to 150.0,
        "Home Subdivision" to 260.0, "Tanco Road / Ayala Highway" to 300.0,
        "Brixton Area" to 230.0
    )

    fun calculateAreaBasedWeight(radiusMeters: Double): Double {
        val area = Math.PI * radiusMeters.pow(2)
        // Density factor: ~0.022 kg per sqm (Research-backed for 5-ton compactors)
        return area * 0.022 
    }

    fun refineVolumeWithPerformance(baseVolume: Double, stops: Int, timeHrs: Double, distKm: Double): Double {
        var multiplier = 1.0
        if (stops > 20) multiplier += 0.2 else if (stops > 10) multiplier += 0.1
        if (timeHrs > 2.0) multiplier += 0.15 else if (timeHrs > 1.0) multiplier += 0.05
        if (distKm > 3.0) multiplier += 0.1
        return baseVolume * multiplier
    }

    /**
     * Estimates waste volume based on area, performance history, and calendar events.
     */
    fun predictWasteVolume(date: String, purokName: String? = null, perf: Map<String, Any>? = null): Double {
        val holidayMultiplier = getHolidayMultiplier(date)
        
        if (purokName != null) {
            val radius = purokZones[purokName] ?: 200.0
            var volume = calculateAreaBasedWeight(radius)
            perf?.let {
                volume = refineVolumeWithPerformance(volume, it["stops"] as? Int ?: 0, it["timeHrs"] as? Double ?: 0.0, it["distKm"] as? Double ?: 0.0)
            }
            return volume * holidayMultiplier
        } else {
            val totalBase = purokZones.values.sumOf { calculateAreaBasedWeight(it) }
            return totalBase * holidayMultiplier
        }
    }

    fun getHolidayMultiplier(date: String): Double {
        return when {
            date.contains("-12-25") || date.contains("-01-01") -> 2.5 // Peak Holiday load
            date.contains("-12-24") || date.contains("-12-31") -> 2.0 // Prep load
            date.contains("-11-01") || date.contains("-11-02") -> 1.5 // Undas
            isWeekend(date) -> 1.2
            else -> 1.0
        }
    }

    private fun isWeekend(date: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val d = sdf.parse(date) ?: return false
            val cal = java.util.Calendar.getInstance().apply { time = d }
            val day = cal.get(java.util.Calendar.DAY_OF_WEEK)
            day == java.util.Calendar.SATURDAY || day == java.util.Calendar.SUNDAY
        } catch (e: Exception) { false }
    }

    fun getTruckCapacityKilos(): Double = 5000.0 // 5 Tons for Compactor

    fun generateInsights(predicted: Double, capacity: Double, isHoliday: Boolean, selected: String?, complaints: Int): List<String> {
        val insights = mutableListOf<String>()
        val area = selected ?: "Overall"
        if (predicted > capacity) insights.add("CRITICAL: Volume for $area exceeds truck capacity. Overflow likely.")
        else if (predicted > capacity * 0.8) insights.add("WARNING: High waste volume for $area. Capacity at 80%+")
        else insights.add("Normal volume expected for $area. Capacity is sufficient.")
        
        if (isHoliday) insights.add("EVENT ALERT: Holiday detected. Expect heavy loads and slower collection.")
        if (complaints > 3) insights.add("ISSUE: High complaint volume in $area. Immediate attention recommended.")
        
        insights.add("OPTIMIZATION: Recommended start time 30 mins earlier to avoid traffic peak.")
        return insights
    }
}
