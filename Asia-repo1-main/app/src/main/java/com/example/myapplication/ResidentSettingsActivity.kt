package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.CustomNotification
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class ResidentSettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var activeDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_resident_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resident_settings_root)) { v, insets ->
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
        findViewById<TextView>(R.id.tv_profile_name).text = user?.name ?: "Juan Dela Cruz"
        findViewById<TextView>(R.id.tv_profile_email).text = user?.email ?: "resident@example.com"
        findViewById<TextView>(R.id.tv_profile_contact).text = user?.phone ?: "09187654321"
        findViewById<TextView>(R.id.tv_profile_purok).text = user?.purok ?: "Purok 2"
    }

    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<android.view.View>(R.id.row_change_password).setOnClickListener {
            showModal(R.layout.dialog_change_password)
        }

        findViewById<android.view.View>(R.id.row_privacy_settings).setOnClickListener {
            showModal(R.layout.dialog_privacy_settings)
        }

        findViewById<android.view.View>(R.id.row_data_management).setOnClickListener {
            showModal(R.layout.dialog_data_management)
        }

        findViewById<android.view.View>(R.id.row_faqs).setOnClickListener {
            showModal(R.layout.dialog_faqs)
        }

        findViewById<android.view.View>(R.id.row_contact_support).setOnClickListener {
            showModal(R.layout.dialog_contact_support)
        }

        findViewById<android.view.View>(R.id.row_about).setOnClickListener {
            showModal(R.layout.dialog_about)
        }
    }

    private fun showModal(layoutResId: Int) {
        if (isFinishing || isDestroyed) return

        val dialogView = LayoutInflater.from(this).inflate(layoutResId, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        activeDialog = alertDialog

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener {
            alertDialog.dismiss()
        }
        
        // ... (rest of logic remains similar but inside the check)

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
                    CustomNotification.showTopNotification(this, "Successful updated password", false)
                    alertDialog.dismiss()
                }
            }
        }

        if (layoutResId == R.layout.dialog_privacy_settings) {
            dialogView.findViewById<MaterialButton>(R.id.btn_save_privacy)?.setOnClickListener {
                CustomNotification.showTopNotification(this, "Privacy settings updated", false)
                alertDialog.dismiss()
            }
        }

        if (layoutResId == R.layout.dialog_data_management) {
            dialogView.findViewById<android.view.View>(R.id.btn_export_data)?.setOnClickListener {
                showActionConfirmation("Export Data", "Are you sure you want to export your personal data?") {
                    CustomNotification.showTopNotification(this, "Data exported successfully", false)
                }
            }
            dialogView.findViewById<android.view.View>(R.id.btn_clear_cache)?.setOnClickListener {
                showActionConfirmation("Clear Cache", "This will remove temporary files. Continue?") {
                    CustomNotification.showTopNotification(this, "Cache cleared successfully", false)
                }
            }
            dialogView.findViewById<android.view.View>(R.id.btn_delete_account)?.setOnClickListener {
                showActionConfirmation("Delete Account", "Are you sure? This action cannot be undone.") {
                    CustomNotification.showTopNotification(this, "Account deletion request sent", false)
                }
            }
        }

        alertDialog.show()
    }

    private fun showActionConfirmation(title: String, message: String, onConfirm: () -> Unit) {
        if (isFinishing || isDestroyed) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_generic_confirmation, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        activeDialog = alertDialog

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tv_confirm_title).text = title
        dialogView.findViewById<TextView>(R.id.tv_confirm_msg).text = message

        dialogView.findViewById<android.view.View>(R.id.btn_confirm_no).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<android.view.View>(R.id.btn_confirm_yes).setOnClickListener {
            onConfirm()
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun showLogoutConfirmation() {
        if (isFinishing || isDestroyed) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation_resident, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        activeDialog = alertDialog

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

    override fun onDestroy() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroy()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_settings

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, ResidentDashboardActivity::class.java))
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
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ResidentComplaintsActivity::class.java))
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