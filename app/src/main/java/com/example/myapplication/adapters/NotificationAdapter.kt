package com.example.myapplication.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.models.SystemNotification
import com.google.android.material.card.MaterialCardView

class NotificationAdapter(
    private val context: Context,
    private var notifications: List<SystemNotification>,
    private val onItemClick: (SystemNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardViewParent: MaterialCardView = view.findViewById(R.id.card_view_parent)
        val iconContainer: View = view.findViewById(R.id.card_icon_bg)
        val ivIcon: ImageView = view.findViewById(R.id.iv_notification_icon)
        val tvTitle: TextView = view.findViewById(R.id.tv_notification_title)
        val tvMessage: TextView = view.findViewById(R.id.tv_notification_message)
        val tvTime: TextView = view.findViewById(R.id.tv_notification_time)
        val viewUnreadDot: View = view.findViewById(R.id.view_unread_dot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]

        holder.tvTitle.text = notification.title
        holder.tvMessage.text = notification.message
        
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            notification.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.tvTime.text = relativeTime
        
        holder.viewUnreadDot.visibility = if (notification.isRead) View.GONE else View.VISIBLE

        // Style based on type
        when (notification.type) {
            "COMPLAINT" -> {
                holder.iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF3E0"))
                holder.ivIcon.setImageResource(R.drawable.ic_complaints)
                holder.ivIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF9800"))
                holder.cardViewParent.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#FFE0B2")))
            }
            "REGISTRATION" -> {
                holder.iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
                holder.ivIcon.setImageResource(android.R.drawable.ic_menu_add)
                holder.ivIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                holder.cardViewParent.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#C8E6C9")))
            }
            "DRIVER_ISSUE" -> {
                holder.iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
                holder.ivIcon.setImageResource(R.drawable.ic_complaints)
                holder.ivIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
                holder.cardViewParent.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#FFCDD2")))
            }
            else -> {
                holder.iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E3F2FD"))
                holder.ivIcon.setImageResource(R.drawable.ic_notifications)
                holder.ivIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
                holder.cardViewParent.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#BBDEFB")))
            }
        }

        holder.itemView.setOnClickListener { onItemClick(notification) }
    }

    override fun getItemCount(): Int = notifications.size

    fun updateData(newList: List<SystemNotification>) {
        notifications = newList
        notifyDataSetChanged()
    }
}
