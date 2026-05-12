package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.models.LoginRequest
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.CustomNotification
import android.view.View
import android.os.Handler
import android.location.LocationManager
import android.provider.Settings
import android.content.Context
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var pbLogin: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        pbLogin = findViewById(R.id.pb_login)

        findViewById<TextView>(R.id.tv_register).setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.btn_sign_in).setOnClickListener {
            performLogin()
        }

        setupPasswordVisibilityToggle()
    }

    private fun setupPasswordVisibilityToggle() {
        val etPassword = findViewById<EditText>(R.id.et_password)
        val ivShowPassword = findViewById<android.widget.ImageView>(R.id.iv_show_password)
        var isPasswordVisible = false

        ivShowPassword.setOnClickListener {
            if (isPasswordVisible) {
                etPassword.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                ivShowPassword.setImageResource(R.drawable.ic_eye_off)
            } else {
                etPassword.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                ivShowPassword.setImageResource(R.drawable.ic_eye)
            }
            isPasswordVisible = !isPasswordVisible
            etPassword.setSelection(etPassword.text.length)
        }
    }

    private fun navigateToDashboard(role: String?) {
        // GPS CHECK - DO NOT REMOVE
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isGpsEnabled) {
            Toast.makeText(this, "Please enable Location/GPS to use the app features", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            return // STOP everything - DO NOT let the user proceed
        }

        when (role?.lowercase()) {
            "admin" -> {
                val intent = Intent(this@MainActivity, AdminDashboardActivity::class.java)
                startActivity(intent)
                finish()
            }
            "resident" -> {
                val intent = Intent(this@MainActivity, ResidentDashboardActivity::class.java)
                startActivity(intent)
                finish()
            }
            "driver" -> {
                val intent = Intent(this@MainActivity, DriverDashboardActivity::class.java)
                startActivity(intent)
                finish()
            }
            else -> {
                CustomNotification.showTopNotification(this, "Unknown role: $role")
            }
        }
    }

    private fun performLogin() {
        val identifier = findViewById<EditText>(R.id.et_username).text.toString().trim()
        val password = findViewById<EditText>(R.id.et_password).text.toString().trim()

        if (identifier.isEmpty()) {
            CustomNotification.showTopNotification(this, "Please enter your username")
            return
        }

        if (password.isEmpty()) {
            CustomNotification.showTopNotification(this, "Please enter your password")
            return
        }

        showLoading(true)
        RetrofitClient.instance.login(LoginRequest(identifier, password)).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(false)
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true) {
                        val user = apiResponse.user
                        if (user != null) {
                            sessionManager.saveUser(user)
                            CustomNotification.showTopNotification(this@MainActivity, "Welcome ${user.name}", false)
                            // Delay slightly so they see the success message
                            Handler(mainLooper).postDelayed({
                                navigateToDashboard(user.role)
                            }, 1000)
                        }
                    } else {
                        val message = apiResponse?.message ?: "Login failed"
                        CustomNotification.showTopNotification(this@MainActivity, message)
                    }
                } else {
                    CustomNotification.showTopNotification(this@MainActivity, "Server Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(false)
                CustomNotification.showTopNotification(this@MainActivity, "Error: ${t.message}")
            }
        })
    }

    private fun showLoading(show: Boolean) {
        pbLogin.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btn_sign_in).isEnabled = !show
    }
}
