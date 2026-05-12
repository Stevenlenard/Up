package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import java.util.*
import java.text.SimpleDateFormat
import androidx.core.util.Pair
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Color
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.models.*
import com.example.myapplication.network.RetrofitClient
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class UserManagementActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var containerUsers: LinearLayout
    private lateinit var tvEmptyState: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var pbLoading: ProgressBar

    private lateinit var btnDatePicker: View
    private lateinit var tvDateRange: TextView
    private lateinit var layoutPurokFilter: View
    private lateinit var actvPurokFilter: AutoCompleteTextView
    private lateinit var switchShowArchived: com.google.android.material.switchmaterial.SwitchMaterial

    private var startDate: Long? = null
    private var endDate: Long? = null
    private var selectedPurokFilter = "All Puroks"
    private var isShowArchivedOnly = false

    private var allResidents = mutableListOf<UserData>()
    private var allDrivers = mutableListOf<UserData>()
    private var allRequests = mutableListOf<UserData>()
    
    private var currentFilteredList = mutableListOf<UserData>()
    private var currentTab = 0

    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user_management)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.user_management_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupTabLayout()
        setupSearch()
        setupBottomNavigation()
        
        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
        database = FirebaseDatabase.getInstance(dbUrl).reference

        fetchUsers()
        fetchFirebaseRequests()

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun initializeViews() {
        etSearch = findViewById(R.id.et_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        containerUsers = findViewById(R.id.container_users)
        tvEmptyState = findViewById(R.id.tv_empty_state)
        tabLayout = findViewById(R.id.tab_layout)
        pbLoading = findViewById(R.id.pb_loading)

        btnDatePicker = findViewById(R.id.btn_date_picker)
        tvDateRange = findViewById(R.id.tv_date_range)
        layoutPurokFilter = findViewById(R.id.layout_purok_filter)
        actvPurokFilter = findViewById(R.id.actv_purok_filter)
        switchShowArchived = findViewById(R.id.switch_show_archived)

        setupFilters()
    }

    private fun setupFilters() {
        btnDatePicker.setOnClickListener {
            showDateSelectionDialog()
        }

        layoutPurokFilter.setOnClickListener {
            actvPurokFilter.showDropDown()
        }

        switchShowArchived.setOnCheckedChangeListener { _, isChecked ->
            isShowArchivedOnly = isChecked
            filterList(etSearch.text.toString())
        }
        
        setupPurokDropdown()
    }

    private fun showDateSelectionDialog() {
        val options = arrayOf("Select Single Date", "Select Date Range", "Clear Filter")
        AlertDialog.Builder(this)
            .setTitle("Date Filter Type")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSingleDatePicker()
                    1 -> showDateRangePicker()
                    2 -> {
                        startDate = null
                        endDate = null
                        tvDateRange.text = "All Time"
                        filterList(etSearch.text.toString())
                    }
                }
            }
            .show()
    }

    private fun showSingleDatePicker() {
        val builder = MaterialDatePicker.Builder.datePicker()
        builder.setTitleText("Select Date")
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            startDate = selection
            // For single date, set end date to end of that same day
            endDate = selection
            
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvDateRange.text = sdf.format(Date(selection))
            
            filterList(etSearch.text.toString())
        }
        picker.show(supportFragmentManager, "single_date_picker")
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Select Date Range")
        
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { range ->
            startDate = range.first
            endDate = range.second
            
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val startStr = sdf.format(Date(startDate!!))
            val endStr = sdf.format(Date(endDate!!))
            tvDateRange.text = "$startStr - $endStr"
            
            filterList(etSearch.text.toString())
        }
        
        picker.show(supportFragmentManager, "date_range_picker")
    }

    private fun setupPurokDropdown() {
        val puroks = arrayOf(
            "All Puroks", "Purok 2", "Purok 3", "Purok 4", "Dos Riles", 
            "Sentro", "San Isidro", "Paraiso", "Riverside", "Kalaw Street", 
            "Home Subdivision", "Tanco Road / Ayala Highway", "Brixton Area"
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, puroks)
        actvPurokFilter.setAdapter(adapter)
        
        actvPurokFilter.setOnItemClickListener { _, _, position, _ ->
            selectedPurokFilter = puroks[position]
            filterList(etSearch.text.toString())
        }
    }

    private fun fetchUsers() {
        pbLoading.visibility = View.VISIBLE
        RetrofitClient.instance.getUsers().enqueue(object : Callback<UsersResponse> {
            override fun onResponse(call: Call<UsersResponse>, response: Response<UsersResponse>) {
                pbLoading.visibility = View.GONE
                if (response.isSuccessful && response.body()?.success == true) {
                    allResidents.clear()
                    allDrivers.clear()
                    
                    response.body()?.residents?.let { allResidents.addAll(it) }
                    response.body()?.users?.let { users ->
                        // Add only drivers to the drivers list
                        allDrivers.addAll(users.filter {
                            it.role.lowercase() == "driver"
                        })
                    }
                    
                    updateList(currentTab)
                } else {
                    Toast.makeText(this@UserManagementActivity, "Failed to fetch users", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<UsersResponse>, t: Throwable) {
                pbLoading.visibility = View.GONE
                Toast.makeText(this@UserManagementActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchFirebaseRequests() {
        // Checking for "registration_requests" in Firebase
        database.child("registration_requests").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allRequests.clear()
                for (reqSnapshot in snapshot.children) {
                    val user = reqSnapshot.getValue(UserData::class.java)
                    if (user != null) {
                        user.requestId = reqSnapshot.key
                        allRequests.add(user)
                    }
                }
                if (currentTab == 2) updateList(2)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("UserManagement", "Firebase Error: ${error.message}")
            }
        })
    }

    private fun setupTabLayout() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                etSearch.text.clear()
                updateSearchHint()
                updateList(currentTab)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateSearchHint() {
        etSearch.hint = when (currentTab) {
            0 -> "Search Residents..."
            1 -> "Search Drivers..."
            2 -> "Search Requests..."
            else -> "Search..."
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterList(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.text.clear()
        }
    }

    private fun filterList(query: String) {
        val sourceList = when (currentTab) {
            0 -> allResidents
            1 -> allDrivers
            2 -> allRequests
            else -> allResidents
        }

        currentFilteredList = sourceList.filter { user ->
            // Search filter
            val matchesSearch = query.isEmpty() || 
                    user.name.contains(query, ignoreCase = true) || 
                    user.email.contains(query, ignoreCase = true) ||
                    (user.phone?.contains(query) ?: false)

            // Archive filter
            val matchesArchive = if (currentTab == 2) true else {
                if (isShowArchivedOnly) user.isArchived == 1 else user.isArchived == 0
            }

            // Purok filter (only for Residents tab)
            val matchesPurok = if (currentTab == 0 && selectedPurokFilter != "All Puroks") {
                user.purok == selectedPurokFilter
            } else true

            // Date filter
            val matchesDate = if (startDate != null && endDate != null) {
                isWithinRange(user.createdAt, startDate!!, endDate!!)
            } else true

            matchesSearch && matchesArchive && matchesPurok && matchesDate
        }.toMutableList()

        displayUsers(currentFilteredList)
    }

    private fun isWithinRange(createdAt: String?, start: Long, end: Long): Boolean {
        if (createdAt == null) return false
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(createdAt) ?: return false
            // MaterialDatePicker range is start of start day to start of end day.
            // We adjust end to include the full last day.
            val endOfDay = end + (24 * 60 * 60 * 1000) - 1
            return date.time in start..endOfDay
        } catch (e: Exception) {
            return false
        }
    }

    private fun updateList(tabIndex: Int) {
        filterList(etSearch.text.toString())
    }

    private fun displayUsers(users: List<UserData>) {
        containerUsers.removeAllViews()
        
        if (users.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
        } else {
            tvEmptyState.visibility = View.GONE
            val inflater = LayoutInflater.from(this)
            
            for (user in users) {
                val view = inflater.inflate(R.layout.item_user_card, containerUsers, false)
                
                view.findViewById<TextView>(R.id.tv_user_name).text = user.name
                view.findViewById<TextView>(R.id.tv_user_email).text = user.email
                
                val userDetailText = when (user.role.lowercase()) {
                    "resident" -> user.purok ?: "No Purok"
                    "driver" -> "License: ${user.licenseNumber ?: "N/A"} | Truck: ${user.preferredTruck ?: "N/A"}"
                    else -> user.role
                }
                view.findViewById<TextView>(R.id.tv_user_detail).text = userDetailText

                val btnArchive = view.findViewById<ImageButton>(R.id.btn_archive)

                if (currentTab == 2) {
                    btnArchive.visibility = View.GONE
                } else {
                    btnArchive.visibility = View.VISIBLE
                    btnArchive.setImageResource(if (user.isArchived == 1) android.R.drawable.ic_menu_revert else android.R.drawable.ic_menu_save)
                    btnArchive.setOnClickListener {
                        toggleArchive(user)
                    }
                }
                
                val indicator = view.findViewById<View>(R.id.view_role_indicator)
                when (user.role.lowercase()) {
                    "driver" -> indicator.setBackgroundResource(R.drawable.driver_icon_bg)
                    "admin" -> indicator.setBackgroundResource(R.drawable.driver_icon_bg)
                    else -> indicator.setBackgroundResource(R.drawable.resident_icon_bg)
                }

                val layoutActions = view.findViewById<LinearLayout>(R.id.layout_actions)
                val ivNextView = view.findViewById<View>(R.id.iv_next)

                if (currentTab == 2) {
                    layoutActions.visibility = View.VISIBLE
                    ivNextView.visibility = View.GONE
                    
                    view.findViewById<Button>(R.id.btn_approve).setOnClickListener {
                        showConfirmDialog(user, true)
                    }
                    view.findViewById<Button>(R.id.btn_decline).setOnClickListener {
                        showConfirmDialog(user, false)
                    }
                } else {
                    layoutActions.visibility = View.GONE
                    ivNextView.visibility = View.VISIBLE
                    view.setOnClickListener {
                        // Details logic for residents/drivers
                    }
                }
                
                containerUsers.addView(view)
            }
        }
    }

    private fun showConfirmDialog(user: UserData, isApprove: Boolean) {
        val title = if (isApprove) "Approve Request" else "Decline Request"
        val message = if (isApprove) 
            "Are you sure you want to approve ${user.name}?" 
            else "Are you sure you want to decline ${user.name}'s request?"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ ->
                if (isApprove) approveRequest(user) else declineRequest(user)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showRequestActionDialog(user: UserData) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_request_action, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tv_dialog_message).text = "Action for ${user.name}?"
        dialogView.findViewById<TextView>(R.id.tv_dialog_email).text = "Email: ${user.email}"
        dialogView.findViewById<TextView>(R.id.tv_dialog_phone).text = "Phone: ${user.phone ?: "N/A"}"
        dialogView.findViewById<TextView>(R.id.tv_dialog_role_info).text = "Role: ${user.role.uppercase()}"

        dialogView.findViewById<Button>(R.id.btn_approve).setOnClickListener {
            approveRequest(user)
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_decline).setOnClickListener {
            declineRequest(user)
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun toggleArchive(user: UserData) {
        val newStatus = if (user.isArchived == 1) 0 else 1
        val action = if (newStatus == 1) "archive" else "unarchive"
        
        AlertDialog.Builder(this)
            .setTitle("Confirm $action")
            .setMessage("Are you sure you want to $action this ${user.role}?")
            .setPositiveButton("Yes") { _, _ ->
                pbLoading.visibility = View.VISIBLE
                val request = ArchiveRequest(user.userId, user.role, newStatus)
                RetrofitClient.instance.archiveUser(request).enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            Toast.makeText(this@UserManagementActivity, response.body()?.message, Toast.LENGTH_SHORT).show()
                            fetchUsers()
                        } else {
                            pbLoading.visibility = View.GONE
                            Toast.makeText(this@UserManagementActivity, "Action failed", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                        pbLoading.visibility = View.GONE
                        Toast.makeText(this@UserManagementActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun approveRequest(user: UserData) {
        val registerRequest = RegisterRequest(
            username = user.username,
            name = user.name,
            email = user.email,
            password = user.password ?: "",
            role = user.role,
            phone = user.phone,
            purok = user.purok,
            completeAddress = user.completeAddress,
            licenseNumber = user.licenseNumber,
            preferredTruck = user.preferredTruck
        )

        pbLoading.visibility = View.VISIBLE
        RetrofitClient.instance.register(registerRequest).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    user.requestId?.let {
                        database.child("registration_requests").child(it).removeValue()
                    }
                    Toast.makeText(this@UserManagementActivity, "User Approved!", Toast.LENGTH_SHORT).show()
                    fetchUsers() // Refresh list
                } else {
                    pbLoading.visibility = View.GONE
                    val errorMsg = response.body()?.message ?: "Approval failed"
                    Toast.makeText(this@UserManagementActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                pbLoading.visibility = View.GONE
                Toast.makeText(this@UserManagementActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun declineRequest(user: UserData) {
        user.requestId?.let {
            database.child("registration_requests").child(it).removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "Request Declined", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_monitor

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_monitor -> true
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
