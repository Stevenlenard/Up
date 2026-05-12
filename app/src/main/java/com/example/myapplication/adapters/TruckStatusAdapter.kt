package com.example.myapplication.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.models.TruckLocation
import com.example.myapplication.utils.PurokManager
import java.util.*

class TruckStatusAdapter(private var trucks: List<TruckLocation>) :
    RecyclerView.Adapter<TruckStatusAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTruckId: TextView = view.findViewById(R.id.tv_truck_id)
        val tvStatusBadge: TextView = view.findViewById(R.id.tv_status_badge)
        val tvLocation: TextView = view.findViewById(R.id.tv_location)
        val tvSpeed: TextView = view.findViewById(R.id.tv_speed)
        val ivTruckIcon: ImageView = view.findViewById(R.id.iv_truck_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_truck_status, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val truck = trucks[position]
        holder.tvTruckId.text = truck.truckId
        holder.tvStatusBadge.text = truck.status.uppercase(Locale.getDefault())
        holder.tvSpeed.text = String.format(Locale.getDefault(), "%.0f km/h", truck.speed)

        // Location - use PurokManager to identify zone
        val currentZone = PurokManager.getZoneAt(truck.latitude, truck.longitude)
        holder.tvLocation.text = currentZone?.name ?: if (truck.speed < 1.0) "Base / Depot" else "Main Road"

        // Status coloring
        val (badgeColor, textColor, iconColor) = when (truck.status.lowercase(Locale.getDefault())) {
            "active" -> Triple("#E8F5E9", "#2E7D32", "#4CAF50")
            "idle" -> Triple("#FFF8E1", "#FFA000", "#FFC107")
            "offline" -> Triple("#F5F5F5", "#616161", "#9E9E9E")
            "full" -> Triple("#FFEBEE", "#C62828", "#F44336")
            else -> Triple("#E3F2FD", "#1976D2", "#2196F3")
        }

        holder.tvStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor(badgeColor))
        holder.tvStatusBadge.setTextColor(Color.parseColor(textColor))
        holder.ivTruckIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(iconColor))
    }

    override fun getItemCount() = trucks.size

    fun updateTrucks(newTrucks: List<TruckLocation>) {
        trucks = newTrucks
        notifyDataSetChanged()
    }
}
