package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.models.ApiResponse
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.CustomNotification
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var layoutStep1: LinearLayout
    private lateinit var layoutStep2: LinearLayout
    private lateinit var layoutStep3: LinearLayout
    
    private lateinit var pbStep1: ProgressBar
    private lateinit var pbStep2: ProgressBar
    private lateinit var pbStep3: ProgressBar

    private lateinit var stepIcon: ImageView
    private lateinit var stepTitle: TextView
    private lateinit var stepSubtitle: TextView

    private var currentPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_forgot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        layoutStep1 = findViewById(R.id.layout_step1)
        layoutStep2 = findViewById(R.id.layout_step2)
        layoutStep3 = findViewById(R.id.layout_step3)
        
        pbStep1 = findViewById(R.id.pb_step1)
        pbStep2 = findViewById(R.id.pb_step2)
        pbStep3 = findViewById(R.id.pb_step3)

        stepIcon = findViewById(R.id.step_icon)
        stepTitle = findViewById(R.id.step_title)
        stepSubtitle = findViewById(R.id.step_subtitle)

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Step 1: Send Token
        findViewById<View>(R.id.btn_send_token).setOnClickListener {
            handleStep1()
        }

        // Step 2: Verify Token
        findViewById<View>(R.id.btn_verify_token).setOnClickListener {
            handleStep2()
        }

        // Step 3: Reset Password
        findViewById<View>(R.id.btn_submit_reset).setOnClickListener {
            handleStep3()
        }
        
        updateHeader(1)
    }

    private fun updateHeader(step: Int) {
        // Set lock icon for all steps as requested
        stepIcon.setImageResource(android.R.drawable.ic_lock_idle_lock)
        
        when(step) {
            1 -> {
                stepTitle.text = "Verify Number"
                stepSubtitle.text = "Step 1 of 3"
            }
            2 -> {
                stepTitle.text = "Enter Token"
                stepSubtitle.text = "Step 2 of 3"
            }
            3 -> {
                stepTitle.text = "Reset Password"
                stepSubtitle.text = "Step 3 of 3"
            }
        }
        // Animated transition for header
        stepIcon.scaleX = 0.5f
        stepIcon.scaleY = 0.5f
        stepIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
    }

    private fun handleStep1() {
        val phone = findViewById<EditText>(R.id.et_phone).text.toString().trim()

        if (phone.isEmpty() || phone.length < 10) {
            CustomNotification.showTopNotification(this, "Please enter a valid contact number")
            return
        }

        showLoading(1, true)
        RetrofitClient.instance.checkPhone(phone).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(1, false)
                if (response.isSuccessful && response.body()?.success == true) {
                    currentPhone = phone
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Token sent to your number", false)
                    layoutStep1.visibility = View.GONE
                    layoutStep2.visibility = View.VISIBLE
                    updateHeader(2)
                } else {
                    val message = response.body()?.message ?: "Number not found in database"
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, message)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(1, false)
                CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Network Error")
            }
        })
    }

    private fun handleStep2() {
        val token = findViewById<EditText>(R.id.et_token).text.toString().trim()

        if (token.isEmpty()) {
            CustomNotification.showTopNotification(this, "Please enter the verification token")
            return
        }

        showLoading(2, true)
        RetrofitClient.instance.verifyToken(currentPhone, token).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(2, false)
                if (response.isSuccessful && response.body()?.success == true) {
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Token matched", false)
                    layoutStep2.visibility = View.GONE
                    layoutStep3.visibility = View.VISIBLE
                    updateHeader(3)
                } else {
                    val message = response.body()?.message ?: "Invalid token"
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, message)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(2, false)
                CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Network Error")
            }
        })
    }

    private fun handleStep3() {
        val newPass = findViewById<EditText>(R.id.et_new_password).text.toString()
        val confirmPass = findViewById<EditText>(R.id.et_confirm_password).text.toString()

        val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$".toRegex()

        if (!newPass.matches(passwordPattern)) {
            CustomNotification.showTopNotification(this, "Password must be 8+ chars with Uppercase, Lowercase, Number, and Symbol")
            return
        }

        if (newPass != confirmPass) {
            CustomNotification.showTopNotification(this, "Passwords do not match")
            return
        }

        showLoading(3, true)
        RetrofitClient.instance.resetPassword(currentPhone, newPass).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                showLoading(3, false)
                if (response.isSuccessful && response.body()?.success == true) {
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Password successfully updated", false)
                    Handler(Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 1500)
                } else {
                    val message = response.body()?.message ?: "Update failed"
                    CustomNotification.showTopNotification(this@ForgotPasswordActivity, message)
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                showLoading(3, false)
                CustomNotification.showTopNotification(this@ForgotPasswordActivity, "Update Error")
            }
        })
    }

    private fun showLoading(step: Int, show: Boolean) {
        when(step) {
            1 -> {
                pbStep1.visibility = if (show) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btn_send_token).isEnabled = !show
            }
            2 -> {
                pbStep2.visibility = if (show) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btn_verify_token).isEnabled = !show
            }
            3 -> {
                pbStep3.visibility = if (show) View.VISIBLE else View.GONE
                findViewById<View>(R.id.btn_submit_reset).isEnabled = !show
            }
        }
    }
}
