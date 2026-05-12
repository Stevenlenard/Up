package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class DriverSettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driver_settings_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)

        setupProfileData()
        setupClickListeners()
        setupBottomNavigation()
    }

    private fun setupProfileData() {
        val user = sessionManager.getUser()
        findViewById<TextView>(R.id.tv_profile_name).text = user?.name ?: "Pedro Santos"
        findViewById<TextView>(R.id.tv_profile_contact).text = user?.phone ?: "09191234567"
        findViewById<TextView>(R.id.tv_profile_truck).text = "GT-001" // Usually from session or API
    }

    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            val intent = Intent(this, DriverDashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_logout_settings).setOnClickListener {
            showLogoutConfirmation()
        }

        // Route Management
        findViewById<android.view.View>(R.id.ll_view_daily_routes).setOnClickListener { showCustomModal(R.layout.dialog_daily_routes) }
        findViewById<android.view.View>(R.id.ll_route_history).setOnClickListener { showCustomModal(R.layout.dialog_route_history) }
        findViewById<android.view.View>(R.id.ll_performance_stats).setOnClickListener { showCustomModal(R.layout.dialog_performance_stats) }

        // Truck Information
        findViewById<android.view.View>(R.id.ll_truck_details).setOnClickListener { showCustomModal(R.layout.dialog_truck_details) }
        findViewById<android.view.View>(R.id.ll_maintenance_schedule).setOnClickListener { showCustomModal(R.layout.dialog_maintenance_schedule) }
        findViewById<android.view.View>(R.id.ll_report_issue).setOnClickListener { showReportIssueDialog() }

        // Account & Security
        findViewById<android.view.View>(R.id.ll_change_password).setOnClickListener {
            showChangePasswordModal()
        }

        // Notifications
        findViewById<android.view.View>(R.id.ll_notification_preferences).setOnClickListener { showCustomModal(R.layout.dialog_notification_preferences) }
        findViewById<android.view.View>(R.id.ll_alert_history).setOnClickListener { showCustomModal(R.layout.dialog_alert_history) }
    }

    private fun showChangePasswordModal() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password_driver, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etNewPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_new_password)
        val etConfirmPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_confirm_password)
        val tilNewPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_new_password)
        val tilConfirmPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_confirm_password)

        dialogView.findViewById<android.view.View>(R.id.btn_save_password)?.setOnClickListener {
            val newPassword = etNewPassword?.text.toString()
            val confirmPassword = etConfirmPassword?.text.toString()

            var isValid = true
            // Password validation regex: 8+ chars, upper, lower, number, symbol
            val passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#\$%^&+=!])(?=\\S+\$).{8,}\$"

            if (!newPassword.matches(passwordPattern.toRegex())) {
                tilNewPassword?.error = "Password must be at least 8 characters with upper, lower, number, and symbol"
                isValid = false
            } else {
                tilNewPassword?.error = null
            }

            if (newPassword != confirmPassword) {
                tilConfirmPassword?.error = "Passwords do not match"
                isValid = false
            } else {
                tilConfirmPassword?.error = null
            }

            if (isValid) {
                com.example.myapplication.utils.CustomNotification.showTopNotification(this, "Successful updated password", false)
                alertDialog.dismiss()
            }
        }

        dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener { alertDialog.dismiss() }
        alertDialog.show()
    }

    private fun showCustomModal(layoutId: Int) {
        val dialogView = LayoutInflater.from(this).inflate(layoutId, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener { alertDialog.dismiss() }
        alertDialog.show()
    }

    private fun showReportIssueDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_report_truck_issue, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.view.View>(R.id.btn_cancel)?.setOnClickListener { alertDialog.dismiss() }
        dialogView.findViewById<android.view.View>(R.id.btn_submit_report)?.setOnClickListener {
            com.example.myapplication.utils.CustomNotification.showTopNotification(this, "Issue report submitted", false)
            alertDialog.dismiss()
        }
        alertDialog.show()
    }

    private fun showLogoutConfirmation() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_confirm_logout).setOnClickListener {
            sessionManager.logout()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        alertDialog.show()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_settings

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DriverDashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_map -> {
                    startActivity(Intent(this, TrackTrucksActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }
}