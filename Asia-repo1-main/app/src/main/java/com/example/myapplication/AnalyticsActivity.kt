package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class AnalyticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.analytics_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            val intent = Intent(this, AdminDashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_export).setOnClickListener {
            showExportModal()
        }

        setupBottomNavigation()
    }

    private fun showExportModal() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export_report, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Setup Report Type Spinner
        val reportTypeSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_report_type)
        val reportTypes = arrayOf("Truck Performance", "Complaints Summary", "Route Efficiency", "Purok Coverage")
        val typeAdapter = ArrayAdapter(this, R.layout.dropdown_item, reportTypes)
        reportTypeSpinner.setAdapter(typeAdapter)
        reportTypeSpinner.setText(reportTypes[0], false)

        // Setup Format Spinner
        val formatSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinner_format)
        val formats = arrayOf("PDF Document (.pdf)", "Excel Spreadsheet (.xlsx)", "CSV File (.csv)")
        val formatAdapter = ArrayAdapter(this, R.layout.dropdown_item, formats)
        formatSpinner.setAdapter(formatAdapter)
        formatSpinner.setText(formats[0], false)

        dialogView.findViewById<android.view.View>(R.id.btn_close)?.setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_export_now)?.setOnClickListener {
            val selectedReport = reportTypeSpinner.text.toString()
            Toast.makeText(this, "Exporting $selectedReport...", Toast.LENGTH_SHORT).show()
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_reports

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
                R.id.nav_reports -> true
                R.id.nav_complaints -> {
                    startActivity(Intent(this, ComplaintsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_users -> {
                    startActivity(Intent(this, UserManagementActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, AdminSettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}