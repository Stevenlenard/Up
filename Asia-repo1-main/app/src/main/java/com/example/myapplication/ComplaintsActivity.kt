package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

import com.example.myapplication.models.ComplaintsResponse
import com.example.myapplication.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.widget.Toast
import android.widget.LinearLayout
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ComplaintsActivity : AppCompatActivity() {

    private lateinit var complaintsContainer: LinearLayout
    private lateinit var tvTotalComplaints: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvInProgressCount: TextView
    private lateinit var tvResolvedCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_complaints)

        val root = findViewById<View>(R.id.complaints_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        complaintsContainer = findViewById(R.id.complaintsContainer)
        tvTotalComplaints = findViewById(R.id.tvTotalComplaints)
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
        fetchComplaints()
    }

    private fun fetchComplaints() {
        RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
            override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val complaints = response.body()?.data ?: emptyList()
                    updateUI(complaints)
                } else {
                    Toast.makeText(this@ComplaintsActivity, "Failed to load complaints", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {
                Toast.makeText(this@ComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUI(complaints: List<com.example.myapplication.models.Complaint>) {
        tvTotalComplaints.text = "${complaints.size} total"
        complaintsContainer.removeAllViews()

        var pendingCount = 0
        var inProgressCount = 0
        var resolvedCount = 0

        for (complaint in complaints) {
            val status = complaint.status.uppercase().replace("_", " ")
            
            when (status) {
                "PENDING" -> pendingCount++
                "IN PROGRESS" -> inProgressCount++
                "RESOLVED" -> resolvedCount++
            }

            val cardView = LayoutInflater.from(this).inflate(R.layout.item_complaint, complaintsContainer, false)
            
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

            tvCategory.text = complaint.category
            tvStatus.text = status
            tvResidentName.text = "${complaint.fullName} • ${complaint.purok}"
            tvDescription.text = complaint.description
            tvDate.text = complaint.createdAt

            when (status) {
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
                    tvAdminResponse.text = complaint.adminResponse ?: "No response provided."
                    
                    layoutResolvedDate.visibility = View.VISIBLE
                    tvResolvedDate.text = "Resolved: ${complaint.updatedAt ?: complaint.createdAt}"
                }
            }

            btnInProgress.setOnClickListener { updateStatus(complaint.id, "in_progress") }
            btnResolve.setOnClickListener { showResolveDialog(complaint.id) }

            complaintsContainer.addView(cardView)
        }

        tvPendingCount.text = pendingCount.toString()
        tvInProgressCount.text = inProgressCount.toString()
        tvResolvedCount.text = resolvedCount.toString()
    }

    private fun updateStatus(id: Int, status: String, response: String? = null) {
        RetrofitClient.instance.updateComplaint(id, status, response).enqueue(object : Callback<com.example.myapplication.models.ApiResponse> {
            override fun onResponse(call: Call<com.example.myapplication.models.ApiResponse>, response: Response<com.example.myapplication.models.ApiResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    fetchComplaints()
                } else {
                    Toast.makeText(this@ComplaintsActivity, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<com.example.myapplication.models.ApiResponse>, t: Throwable) {
                Toast.makeText(this@ComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showResolveDialog(id: Int) {
        val editText = android.widget.EditText(this)
        editText.hint = "Enter admin response"
        android.app.AlertDialog.Builder(this)
            .setTitle("Resolve Complaint")
            .setView(editText)
            .setPositiveButton("Resolve") { _, _ ->
                updateStatus(id, "RESOLVED", editText.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (bottomNavigationView != null) {
            bottomNavigationView.selectedItemId = R.id.nav_complaints

            bottomNavigationView.setOnItemSelectedListener { item ->
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
}