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
import com.example.myapplication.models.SystemNotification
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ComplaintsActivity : AppCompatActivity() {

    private lateinit var complaintsContainer: LinearLayout
    private lateinit var tvTotalComplaints: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvInProgressCount: TextView
    private lateinit var tvResolvedCount: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var tvHeaderTitle: TextView

    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(dbUrl)
    private val issuesList = mutableListOf<SystemNotification>()
    private var residentComplaints = listOf<com.example.myapplication.models.Complaint>()

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
        tabLayout = findViewById(R.id.tabLayout)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            val intent = Intent(this, AdminDashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateUI()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        setupBottomNavigation()
        fetchComplaints()
        fetchDriverIssues()

        // Handle intent extra to select tab
        val initialTab = intent.getIntExtra("SELECTED_TAB", 0)
        tabLayout.getTabAt(initialTab)?.select()
    }

    private fun fetchComplaints() {
        RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
            override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    residentComplaints = response.body()?.data ?: emptyList()
                    if (tabLayout.selectedTabPosition == 0) updateUI()
                } else {
                    Toast.makeText(this@ComplaintsActivity, "Failed to load complaints", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {
                Toast.makeText(this@ComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchDriverIssues() {
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
                    issuesList.sortByDescending { it.timestamp }
                    if (tabLayout.selectedTabPosition == 1) updateUI()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateUI() {
        complaintsContainer.removeAllViews()
        var pending = 0
        var inProgress = 0
        var resolved = 0

        if (tabLayout.selectedTabPosition == 0) {
            tvHeaderTitle.text = "Resident Complaints"
            tvTotalComplaints.text = "${residentComplaints.size} total"
            
            for (complaint in residentComplaints) {
                val status = complaint.status.uppercase().replace("_", " ")
                when (status) {
                    "PENDING" -> pending++
                    "IN PROGRESS" -> inProgress++
                    "RESOLVED" -> resolved++
                }
                addComplaintCard(complaint)
            }
        } else {
            tvHeaderTitle.text = "Driver Issues"
            tvTotalComplaints.text = "${issuesList.size} total"
            
            for (issue in issuesList) {
                val status = (issue.status ?: "PENDING").uppercase()
                when (status) {
                    "PENDING", "" -> pending++
                    "IN PROGRESS" -> inProgress++
                    "RESOLVED" -> resolved++
                }
                addIssueCard(issue)
            }
        }

        tvPendingCount.text = pending.toString()
        tvInProgressCount.text = inProgress.toString()
        tvResolvedCount.text = resolved.toString()
    }

    private fun addComplaintCard(complaint: com.example.myapplication.models.Complaint) {
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
        val status = complaint.status.uppercase().replace("_", " ")
        tvStatus.text = status
        tvResidentName.text = "${complaint.fullName} • ${complaint.purok}"
        tvDescription.text = complaint.description
        tvDate.text = complaint.createdAt

        applyStatusStyle(tvStatus, status, layoutActions, btnInProgress, layoutAdminResponse, tvAdminResponse, complaint.adminResponse, layoutResolvedDate, tvResolvedDate, complaint.updatedAt ?: complaint.createdAt)

        btnInProgress.setOnClickListener { updateComplaintStatus(complaint.id, "in_progress") }
        btnResolve.setOnClickListener { showResolveDialog(complaint.id.toString(), true) }

        complaintsContainer.addView(cardView)
    }

    private fun addIssueCard(issue: SystemNotification) {
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

        tvCategory.text = issue.title.replace("New Driver Issue: ", "")
        val status = (issue.status ?: "PENDING").uppercase()
        tvStatus.text = if (status.isEmpty()) "PENDING" else status
        tvResidentName.text = issue.message.substringBefore(" reported an issue:")
        tvDescription.text = issue.message.substringAfter(" reported an issue: ")
        
        tvDate.text = android.text.format.DateUtils.getRelativeTimeSpanString(issue.timestamp, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)

        applyStatusStyle(tvStatus, tvStatus.text.toString(), layoutActions, btnInProgress, layoutAdminResponse, tvAdminResponse, issue.adminResponse, layoutResolvedDate, tvResolvedDate, "Resolved")

        btnInProgress.setOnClickListener { updateIssueStatus(issue.id, "IN PROGRESS") }
        btnResolve.setOnClickListener { showResolveDialog(issue.id, false) }

        complaintsContainer.addView(cardView)
    }

    private fun applyStatusStyle(tvStatus: TextView, status: String, layoutActions: View, btnInProgress: View, layoutAdminResponse: View, tvAdminResponse: TextView, responseText: String?, layoutResolvedDate: View, tvResolvedDate: TextView, dateText: String) {
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
                tvAdminResponse.text = responseText ?: "No response provided."
                layoutResolvedDate.visibility = View.VISIBLE
                tvResolvedDate.text = if (dateText == "Resolved") "Resolved" else "Resolved: $dateText"
            }
        }
    }

    private fun updateComplaintStatus(id: Int, status: String, response: String? = null) {
        RetrofitClient.instance.updateComplaint(id, status, response).enqueue(object : Callback<com.example.myapplication.models.ApiResponse> {
            override fun onResponse(call: Call<com.example.myapplication.models.ApiResponse>, response: Response<com.example.myapplication.models.ApiResponse>) {
                if (response.isSuccessful && response.body()?.success == true) fetchComplaints()
                else Toast.makeText(this@ComplaintsActivity, "Update failed", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(call: Call<com.example.myapplication.models.ApiResponse>, t: Throwable) {
                Toast.makeText(this@ComplaintsActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateIssueStatus(id: String, status: String, response: String? = null) {
        val updates = mutableMapOf<String, Any>("status" to status)
        if (response != null) updates["adminResponse"] = response
        database.getReference("notifications").child(id).updateChildren(updates)
            .addOnSuccessListener { Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show() }
    }

    private fun showResolveDialog(id: String, isComplaint: Boolean) {
        val editText = android.widget.EditText(this)
        editText.hint = "Enter admin response"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Resolve")
            .setView(editText)
            .setPositiveButton("Resolve") { _, _ ->
                if (isComplaint) updateComplaintStatus(id.toInt(), "RESOLVED", editText.text.toString())
                else updateIssueStatus(id, "RESOLVED", editText.text.toString())
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
                    R.id.nav_monitor -> { startActivity(Intent(this, AdminDashboardActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                    R.id.nav_track -> { startActivity(Intent(this, TrackTrucksActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                    R.id.nav_reports -> { startActivity(Intent(this, AnalyticsActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                    R.id.nav_complaints -> true
                    R.id.nav_settings -> { startActivity(Intent(this, AdminSettingsActivity::class.java)); overridePendingTransition(0, 0); finish(); true }
                    else -> false
                }
            }
        }
    }
}