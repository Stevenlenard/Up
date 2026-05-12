package com.example.myapplication.utils

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.example.myapplication.R
import com.google.android.material.snackbar.Snackbar

object CustomNotification {

    fun showTopNotification(activity: Activity, message: String, isError: Boolean = true) {
        val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        
        val snackbar = Snackbar.make(rootView, "", Snackbar.LENGTH_LONG)
        val layout = snackbar.view as Snackbar.SnackbarLayout
        
        // Hide default text
        val textView = layout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.visibility = View.INVISIBLE

        // Inflate custom layout
        val inflater = LayoutInflater.from(activity)
        val customView = inflater.inflate(R.layout.layout_top_notification, null)
        
        val tvMessage = customView.findViewById<TextView>(R.id.tv_notification_message)
        tvMessage.text = message
        
        val container = customView.findViewById<View>(R.id.notification_container)
        if (isError) {
            container.setBackgroundResource(R.drawable.bg_notification_error)
        } else {
            container.setBackgroundResource(R.drawable.bg_notification_success)
        }

        layout.setPadding(0, 0, 0, 0)
        layout.setBackgroundColor(Color.TRANSPARENT)
        layout.addView(customView, 0)
        
        // Position at top
        val params = layout.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.TOP
        params.setMargins(24, 60, 24, 0)
        layout.layoutParams = params

        snackbar.show()
    }
}
