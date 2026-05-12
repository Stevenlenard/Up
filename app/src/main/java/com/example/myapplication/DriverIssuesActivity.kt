package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.models.SystemNotification
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DriverIssuesActivity : AppCompatActivity() {

    private lateinit var issuesContainer: LinearLayout
    private lateinit var tvTotalIssues: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvInProgressCount: TextView
    private lateinit var tvResolvedCount: TextView
    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(dbUrl)
    private val issuesList = mutableListOf<SystemNotification>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_issues)

        val root = findViewById<View>(R.id.driver_issues_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        issuesContainer = findViewById(R.id.issuesContainer)
        tvTotalIssues = findViewById(R.id.tvTotalIssues)
        tvPendingCount = findViewById(R.id.tvPendingCount)
        tvInProgressCount = findViewById(R.id.tvInProgressCount)
        tvResolvedCount = findViewById(R.id.tvResolvedCount)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, AdminDashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        setupBottomNavigation()
        fetchIssues()
    }

    private fun fetchIssues() {
        database.getReference("notifications")
            .orderByChild("type")
            .equalTo("DRIVER_ISSUE")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    issuesList.clear()
                    for (child in snapshot.children) {
                        val notification = child.getValue(SystemNotification::class.java)
                        if (notification != null) {
                            notification.id = child.key ?: ""
                            issuesList.add(notification)
                        }
                    }
                    // Sort by timestamp descending
                    issuesList.sortByDescending { it.timestamp }
                    updateUI()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@DriverIssuesActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateUI() {
        tvTotalIssues.text = "${issuesList.size} total"
        issuesContainer.removeAllViews()

        var pendingCount = 0
        var inProgressCount = 0
        var resolvedCount = 0

        for (issue in issuesList) {
            val status = issue.status.uppercase()
            
            when (status) {
                "PENDING", "" -> pendingCount++
                "IN PROGRESS" -> inProgressCount++
                "RESOLVED" -> resolvedCount++
            }

            val cardView = LayoutInflater.from(this).inflate(R.layout.item_complaint, issuesContainer, false)
            
            val tvCategory = cardView.findViewById<TextView>(R.id.tvCategory)
            val tvStatus = cardView.findViewById<TextView>(R.id.tvStatus)
            val tvResidentName = cardView.findViewById<TextView>(R.id.tvResidentName)
            val tvDescription = cardView.findViewById<TextView>(R.id.tvDescription)
            val tvDate = cardView.findViewById<TextView>(R.id.tvDate)
            val layoutActions = cardView.findViewById<LinearLayout>(R.id.layoutActions)
            val layoutAdminResponse = cardView.findViewById<LinearLayout>(R.id.layoutAdminResponse)
            val tvAdminResponse = cardView.findViewById<TextView>(R.id.tvAdminResponse)
            val btnInProgress = cardView.findViewById<View>(R.id.btnInProgress)
            val btnResolve = cardView.findViewById<View>(R.id.btnResolve)
            val layoutResolvedDate = cardView.findViewById<LinearLayout>(R.id.layoutResolvedDate)
            val tvResolvedDate = cardView.findViewById<TextView>(R.id.tvResolvedDate)

            tvCategory.text = issue.title.replace("New Driver Issue: ", "")
            tvStatus.text = if (status.isEmpty()) "PENDING" else status
            tvResidentName.text = issue.message.substringBefore(" reported an issue:")
            tvDescription.text = issue.message.substringAfter(" reported an issue: ")
            
            val relativeTime = android.text.format.DateUtils.getRelativeTimeSpanString(
                issue.timestamp,
                System.currentTimeMillis(),
                android.text.format.DateUtils.MINUTE_IN_MILLIS
            )
            tvDate.text = relativeTime

            when (tvStatus.text.toString()) {
                "PENDING" -> {
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#F9A825"))
                    tvStatus.background.setTint(android.graphics.Color.parseColor("#FFF9C4"))
                    layoutActions.visibility = View.VISIBLE
                    btnInProgress.visibility = View.VISIBLE
                    layoutAdminResponse.visibility = View.GONE
                    layoutResolvedDate.visibility = View.GONE
                }
                "IN PROGRESS" -> {
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#1E88E5"))
                    tvStatus.background.setTint(android.graphics.Color.parseColor("#E3F2FD"))
                    layoutActions.visibility = View.VISIBLE
                    btnInProgress.visibility = View.GONE
                    layoutAdminResponse.visibility = View.GONE
                    layoutResolvedDate.visibility = View.GONE
                }
                "RESOLVED" -> {
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#43A047"))
                    tvStatus.background.setTint(android.graphics.Color.parseColor("#E8F5E9"))
                    layoutActions.visibility = View.GONE
                    layoutAdminResponse.visibility = View.VISIBLE
                    tvAdminResponse.text = issue.adminResponse ?: "No response provided."
                    
                    layoutResolvedDate.visibility = View.VISIBLE
                    tvResolvedDate.text = "Resolved"
                }
            }

            btnInProgress.setOnClickListener { updateStatus(issue.id, "IN PROGRESS") }
            btnResolve.setOnClickListener { showResolveDialog(issue.id) }

            issuesContainer.addView(cardView)
        }

        tvPendingCount.text = pendingCount.toString()
        tvInProgressCount.text = inProgressCount.toString()
        tvResolvedCount.text = resolvedCount.toString()
    }

    private fun updateStatus(id: String, status: String, response: String? = null) {
        val updates = mutableMapOf<String, Any>(
            "status" to status
        )
        if (response != null) {
            updates["adminResponse"] = response
        }
        
        database.getReference("notifications").child(id).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showResolveDialog(id: String) {
        val editText = android.widget.EditText(this)
        editText.hint = "Enter admin response"
        AlertDialog.Builder(this)
            .setTitle("Resolve Issue")
            .setView(editText)
            .setPositiveButton("Resolve") { _, _ ->
                updateStatus(id, "RESOLVED", editText.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_complaints

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
                R.id.nav_complaints -> true
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