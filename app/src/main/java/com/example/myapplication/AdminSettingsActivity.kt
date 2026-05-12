package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class AdminSettingsActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupHeader()
        setupProfile()
        setupBottomNavigation()
        setupSettingsActions()

        findViewById<MaterialButton>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupSettingsActions() {
        val switchEmail = findViewById<SwitchMaterial>(R.id.switch_email_notifications)
        val switchApp = findViewById<SwitchMaterial>(R.id.switch_app_notifications)

        switchEmail.isChecked = sessionManager.isEmailNotificationsEnabled()
        switchApp.isChecked = sessionManager.isAppNotificationsEnabled()

        switchEmail.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setEmailNotificationsEnabled(isChecked)
            Toast.makeText(this, "Email notifications ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        switchApp.setOnCheckedChangeListener { _, isChecked ->
            sessionManager.setAppNotificationsEnabled(isChecked)
            Toast.makeText(this, "App notifications ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.view.View>(R.id.row_change_password).setOnClickListener {
            showModal(R.layout.dialog_change_password)
        }
        findViewById<android.view.View>(R.id.row_two_factor).setOnClickListener {
            showModal(R.layout.dialog_two_factor)
        }
        findViewById<android.view.View>(R.id.row_access_logs).setOnClickListener {
            showModal(R.layout.dialog_access_logs)
        }
        findViewById<android.view.View>(R.id.row_user_permissions).setOnClickListener {
            showModal(R.layout.dialog_user_permissions)
        }
    }

    private fun showModal(layoutResId: Int) {
        val dialogView = LayoutInflater.from(this).inflate(layoutResId, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener {
            alertDialog.dismiss()
        }

        // Setup status dropdown if present in the layout
        val statusSpinner = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.spinner_status)
        if (statusSpinner != null) {
            val statuses = arrayOf("Active", "Inactive", "Maintenance")
            val adapter = android.widget.ArrayAdapter(this, R.layout.dropdown_item, statuses)
            statusSpinner.setAdapter(adapter)
            statusSpinner.setText(statuses[0], false)
        }

        if (layoutResId == R.layout.dialog_change_password) {
            val etNewPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_new_password)
            val etConfirmPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_confirm_password)
            val tilNewPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_new_password)
            val tilConfirmPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_confirm_password)

            dialogView.findViewById<MaterialButton>(R.id.btn_save_password)?.setOnClickListener {
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
                    // Handle password update
                    alertDialog.dismiss()
                }
            }
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

        dialogView.findViewById<android.widget.Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<android.widget.Button>(R.id.btn_confirm_logout).setOnClickListener {
            sessionManager.logout()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        alertDialog.show()
    }

    private fun setupHeader() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            val intent = Intent(this, AdminDashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun setupProfile() {
        val user = sessionManager.getUser()
        findViewById<TextView>(R.id.tv_profile_name).text = user?.name ?: "Admin User"
        findViewById<TextView>(R.id.tv_profile_email).text = user?.email ?: "admin@balintawak.gov"
        findViewById<TextView>(R.id.tv_profile_contact).text = user?.phone ?: "09171234567"
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_settings

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_monitor -> {
                    startActivity(Intent(this, AdminDashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_track -> {
                    startActivity(Intent(this, TrackTrucksActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_reports -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ComplaintsActivity::class.java))
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