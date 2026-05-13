package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class DriverIssuesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_issues)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driver_issues_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupBottomNavigation()
        setupClickListeners()
        populateSampleIssues()
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.btn_new_issue).setOnClickListener {
            // Static UI only, no action for now
        }
    }

    private fun populateSampleIssues() {
        val container = findViewById<LinearLayout>(R.id.issues_container)
        val inflater = LayoutInflater.from(this)

        // 1. Vehicle Problem (Pending)
        addIssueCard(container, inflater, 
            "Vehicle Problem", 
            "Brake pads need replacement. Squeaking sound when stopping.", 
            "PENDING", 
            "2026-05-13 08:45:10", 
            null, 
            null
        )

        // 2. Equipment Issue (In Progress)
        addIssueCard(container, inflater, 
            "Equipment Issue", 
            "GPS tracker is disconnecting frequently.", 
            "IN PROGRESS", 
            "2026-05-12 23:19:03", 
            null, 
            null
        )

        // 3. Spilled Waste (Resolved)
        addIssueCard(container, inflater, 
            "Spilled Waste", 
            "Hydraulic leak caused some waste to spill during collection.", 
            "RESOLVED", 
            "2026-05-08 09:04:15", 
            "2026-05-08 14:30:00", 
            "Maintenance crew dispatched. Leak fixed and area cleaned."
        )
    }

    private fun addIssueCard(
        container: LinearLayout, 
        inflater: LayoutInflater,
        title: String,
        desc: String,
        status: String,
        timestamp: String,
        resolvedDate: String?,
        adminResponse: String?
    ) {
        val card = inflater.inflate(R.layout.item_issue_card, container, false)
        
        card.findViewById<TextView>(R.id.tv_issue_title).text = title
        card.findViewById<TextView>(R.id.tv_issue_description).text = desc
        card.findViewById<TextView>(R.id.tv_timestamp).text = timestamp
        
        val statusBadge = card.findViewById<TextView>(R.id.tv_status_badge)
        statusBadge.text = status
        
        when(status) {
            "PENDING" -> {
                statusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFFBEE"))
                statusBadge.setTextColor(android.graphics.Color.parseColor("#F9A825"))
            }
            "IN PROGRESS" -> {
                statusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F1F8FE"))
                statusBadge.setTextColor(android.graphics.Color.parseColor("#1E88E5"))
            }
            "RESOLVED" -> {
                statusBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F1F8F1"))
                statusBadge.setTextColor(android.graphics.Color.parseColor("#43A047"))
                
                if (resolvedDate != null) {
                    val tvResolved = card.findViewById<TextView>(R.id.tv_resolved_date)
                    tvResolved.text = "Resolved: $resolvedDate"
                    tvResolved.visibility = View.VISIBLE
                }
                
                if (adminResponse != null) {
                    card.findViewById<View>(R.id.admin_response_layout).visibility = View.VISIBLE
                    card.findViewById<TextView>(R.id.tv_admin_response).text = adminResponse
                }
            }
        }
        
        container.addView(card)
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_issues

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_issues -> true
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DriverDashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_map -> {
                    // Navigate to Dashboard then switch to Map tab if needed, 
                    // or just open Dashboard and let user switch.
                    // For now, same as Home but we could pass an extra.
                    val intent = Intent(this, DriverDashboardActivity::class.java)
                    intent.putExtra("TARGET_TAB", R.id.nav_map)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    val intent = Intent(this, DriverDashboardActivity::class.java)
                    intent.putExtra("TARGET_TAB", R.id.nav_settings)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
