package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.utils.CustomNotification
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class DriverRegisterActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etConfirmPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etFullName: EditText
    private lateinit var etLicenseNumber: EditText
    private lateinit var etContactNumber: EditText
    private lateinit var etTruckAssignment: EditText
    private lateinit var btnSubmit: View
    private lateinit var pbRegister: ProgressBar
    private lateinit var tvSubmitText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.driver_register_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_cancel).setOnClickListener { finish() }
        btnSubmit.setOnClickListener { submitRequest() }
    }

    private fun initViews() {
        etUsername = findViewById(R.id.et_username)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        etFullName = findViewById(R.id.et_full_name)
        etLicenseNumber = findViewById(R.id.et_license_number)
        etContactNumber = findViewById(R.id.et_contact_number)
        etTruckAssignment = findViewById(R.id.et_truck_assignment)
        btnSubmit = findViewById(R.id.btn_submit)
        pbRegister = findViewById(R.id.pb_register)
        tvSubmitText = findViewById(R.id.tv_submit_text)
    }

    private fun submitRequest() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val fullName = etFullName.text.toString().trim()
        val contactNumber = etContactNumber.text.toString().trim()
        val licenseNumber = etLicenseNumber.text.toString().trim()
        val truckAssignment = etTruckAssignment.text.toString().trim()

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || fullName.isEmpty() || contactNumber.isEmpty() || licenseNumber.isEmpty()) {
            CustomNotification.showTopNotification(this, "Please fill in all required fields")
            return
        }

        if (password != etConfirmPassword.text.toString()) {
            CustomNotification.showTopNotification(this, "Passwords do not match")
            return
        }

        showLoading(true)

        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
        val database = FirebaseDatabase.getInstance(dbUrl).getReference("registration_requests")
        val requestId = database.push().key ?: System.currentTimeMillis().toString()

        val requestData = mapOf(
            "userId" to 0,
            "username" to username,
            "name" to fullName,
            "email" to email,
            "password" to password,
            "role" to "driver",
            "phone" to contactNumber,
            "licenseNumber" to licenseNumber,
            "preferredTruck" to truckAssignment,
            "status" to "pending",
            "timestamp" to ServerValue.TIMESTAMP
        )

        database.child(requestId).setValue(requestData)
            .addOnSuccessListener {
                showLoading(false)
                CustomNotification.showTopNotification(this, "Driver Application Sent! Wait for Admin Approval.", false)
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
            }
            .addOnFailureListener {
                showLoading(false)
                CustomNotification.showTopNotification(this, "Failed to send request: ${it.message}")
            }
    }

    private fun showLoading(show: Boolean) {
        pbRegister.visibility = if (show) View.VISIBLE else View.GONE
        tvSubmitText.text = if (show) "Submitting..." else "Register as Driver"
        btnSubmit.isEnabled = !show
    }
}
