package com.example.myapplication.utils

import android.location.Location

object PurokManager {
    data class PurokZone(
        val name: String, 
        val latitude: Double, 
        val longitude: Double, 
        val radiusMeters: Double,
        val checkpoints: List<Pair<Double, Double>> = emptyList()
    )

    val purokZones = listOf(
        PurokZone("Purok 2", 13.9402, 121.1638, 220.0, listOf(13.9405 to 121.1640, 13.9398 to 121.1635)),
        PurokZone("Purok 3", 13.9375, 121.1660, 230.0, listOf(13.9378 to 121.1665, 13.9370 to 121.1655)),
        PurokZone("Purok 4", 13.9430, 121.1625, 250.0, listOf(13.9435 to 121.1630, 13.9425 to 121.1620)),
        PurokZone("Dos Riles", 13.9358, 121.1595, 200.0, listOf(13.9360 to 121.1600, 13.9355 to 121.1590)),
        PurokZone("Sentro", 13.9388, 121.1645, 180.0, listOf(13.9390 to 121.1650, 13.9385 to 121.1640)),
        PurokZone("San Isidro", 13.9342, 121.1620, 210.0, listOf(13.9345 to 121.1625, 13.9338 to 121.1615)),
        PurokZone("Paraiso", 13.9325, 121.1602, 200.0, listOf(13.9328 to 121.1608, 13.9320 to 121.1595)),
        PurokZone("Riverside", 13.9365, 121.1678, 240.0, listOf(13.9368 to 121.1685, 13.9360 to 121.1670)),
        PurokZone("Kalaw Street", 13.9395, 121.1580, 150.0, listOf(13.9398 to 121.1585, 13.9390 to 121.1575)),
        PurokZone("Home Subdivision", 13.9415, 121.1565, 260.0, listOf(13.9418 to 121.1570, 13.9410 to 121.1560)),
        PurokZone("Tanco Road / Ayala Highway", 13.9312, 121.1705, 300.0, listOf(13.9315 to 121.1710, 13.9308 to 121.1700)),
        PurokZone("Brixton Area", 13.9382, 121.1552, 230.0, listOf(13.9385 to 121.1558, 13.9378 to 121.1545))
    )

    fun getZoneAt(lat: Double, lng: Double): PurokZone? {
        val loc = Location("").apply {
            latitude = lat
            longitude = lng
        }
        for (zone in purokZones) {
            val zoneLoc = Location("").apply {
                latitude = zone.latitude
                longitude = zone.longitude
            }
            if (loc.distanceTo(zoneLoc) <= zone.radiusMeters) return zone
        }
        return null
    }
}
