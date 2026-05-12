package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.models.RegisterRequest
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.CustomNotification
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ResidentRegisterActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etConfirmPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etFullName: EditText
    private lateinit var etContactNumber: EditText
    private lateinit var spinnerPurok: Spinner
    private lateinit var etAddress: EditText
    private lateinit var btnSubmit: View
    private lateinit var pbRegister: ProgressBar
    private lateinit var tvSubmitText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_resident_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.resident_register_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupPurokSpinner()

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_cancel).setOnClickListener { finish() }
        btnSubmit.setOnClickListener { performRegistration() }
    }

    private fun initViews() {
        etUsername = findViewById(R.id.et_username)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        etFullName = findViewById(R.id.et_full_name)
        etContactNumber = findViewById(R.id.et_contact_number)
        spinnerPurok = findViewById(R.id.spinner_purok)
        etAddress = findViewById(R.id.et_address)
        btnSubmit = findViewById(R.id.btn_submit)
        pbRegister = findViewById(R.id.pb_register)
        tvSubmitText = findViewById(R.id.tv_submit_text)
    }

    private fun setupPurokSpinner() {
        val puroks = arrayOf(
            "Choose Purok...", "Purok 2", "Purok 3", "Purok 4", "Dos Riles", 
            "Sentro", "San Isidro", "Paraiso", "Riverside", "Kalaw Street", 
            "Home Subdivision", "Tanco Road / Ayala Highway", "Brixton Area"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, puroks)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPurok.adapter = adapter
    }

    private fun performRegistration() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val fullName = etFullName.text.toString().trim()
        val phone = etContactNumber.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val purok = spinnerPurok.selectedItem.toString()

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || fullName.isEmpty() || phone.isEmpty() || address.isEmpty() || spinnerPurok.selectedItemPosition == 0) {
            CustomNotification.showTopNotification(this, "Please fill in all fields")
            return
        }

        if (password != etConfirmPassword.text.toString()) {
            CustomNotification.showTopNotification(this, "Passwords do not match")
            return
        }

        showLoading(true)

        val request = RegisterRequest(
            username = username,
            name = fullName,
            email = email,
            password = password,
            role = "resident",
            phone = phone,
            purok = purok,
            completeAddress = address
        )

        RetrofitClient.instance.register(request).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(false)
                if (response.isSuccessful && response.body()?.success == true) {
                    CustomNotification.showTopNotification(this@ResidentRegisterActivity, "Registration Successful!", false)
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
                } else {
                    val msg = response.body()?.message ?: "Registration Failed"
                    CustomNotification.showTopNotification(this@ResidentRegisterActivity, msg)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(false)
                CustomNotification.showTopNotification(this@ResidentRegisterActivity, "Connection Error")
            }
        })
    }

    private fun showLoading(show: Boolean) {
        pbRegister.visibility = if (show) View.VISIBLE else View.GONE
        tvSubmitText.text = if (show) "Submitting..." else "Submit Registration"
        btnSubmit.isEnabled = !show
    }
}
