package com.example.myapplication

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.CustomNotification
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FileComplaintActivity : AppCompatActivity() {

    private lateinit var spinnerCategory: Spinner
    private lateinit var etDescription: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: ImageButton
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_complaint)

        sessionManager = SessionManager(this)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        etDescription = findViewById(R.id.etDescription)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnBack = findViewById(R.id.btnBack)

        // Setup Spinner
        val categories = arrayOf("Missed Collection", "Schedule Issue", "Improper Waste Disposal", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter

        btnBack.setOnClickListener { finish() }

        btnSubmit.setOnClickListener {
            submitComplaint()
        }
    }

    private fun submitComplaint() {
        val category = spinnerCategory.selectedItem.toString()
        val description = etDescription.text.toString().trim()

        if (description.isEmpty()) {
            CustomNotification.showTopNotification(this, "Please enter a description")
            return
        }

        btnSubmit.isEnabled = false
        btnSubmit.text = "Submitting..."

        val residentId = sessionManager.getUser()?.userId?.toString() ?: "0"

            RetrofitClient.instance.fileComplaint(residentId, category, description)
                .enqueue(object : Callback<ApiResponse> {
                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit Complaint"
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        // Notify Admin
                        val user = sessionManager.getUser()
                        val notification = com.example.myapplication.models.SystemNotification(
                            type = "COMPLAINT",
                            title = "New Complaint",
                            message = "${user?.name ?: "Resident"} filed a new complaint: $category",
                            timestamp = System.currentTimeMillis(),
                            isRead = false,
                            relatedId = ""
                        )
                        val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
                        com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                            .getReference("notifications").push().setValue(notification)

                        CustomNotification.showTopNotification(this@FileComplaintActivity, "Complaint filed successfully", false)
                        android.os.Handler(mainLooper).postDelayed({ finish() }, 1500)
                    } else {
                        val msg = response.body()?.message ?: "Unknown Error"
                        CustomNotification.showTopNotification(this@FileComplaintActivity, "Failed: $msg")
                    }
                }

                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit Complaint"
                    CustomNotification.showTopNotification(this@FileComplaintActivity, "Error: ${t.message}")
                }
            })
    }
}
