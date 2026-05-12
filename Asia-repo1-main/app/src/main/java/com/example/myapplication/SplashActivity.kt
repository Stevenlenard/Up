package com.example.myapplication

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.Toast
import android.location.LocationManager
import android.provider.Settings
import android.content.Context
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.models.ComplaintsResponse
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.utils.SessionManager
import com.example.myapplication.utils.CustomNotification
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SplashActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var isNetworkCheckDone = false
    private var isMinTimeDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fix: Don't show splash if app is already running in background
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN == intent.action) {
            finish()
            return
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splash_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sessionManager = SessionManager(this)

        val ivTruck = findViewById<ImageView>(R.id.iv_truck)
        val speedLines = findViewById<View>(R.id.speed_lines)
        val line1 = findViewById<View>(R.id.line1)
        val line2 = findViewById<View>(R.id.line2)
        val line3 = findViewById<View>(R.id.line3)
        val logoContainer = findViewById<View>(R.id.logo_container)

        // Fade in logo
        logoContainer.animate().alpha(1f).setDuration(1000).start()

        // Animation logic for truck and air/speed lines
        ivTruck.post {
            val screenWidth = resources.displayMetrics.widthPixels.toFloat()
            val truckWidth = ivTruck.width.toFloat()

            // 1. Horizontal movement for Truck
            val moveAnimator = ObjectAnimator.ofFloat(ivTruck, "translationX", -truckWidth, screenWidth)
            moveAnimator.duration = 1800 // Slightly faster for better speed feel
            moveAnimator.interpolator = LinearInterpolator()
            moveAnimator.repeatCount = ValueAnimator.INFINITE
            moveAnimator.start()

            // 2. Air/Speed lines following the truck
            val linesMoveAnimator = ObjectAnimator.ofFloat(speedLines, "translationX", -truckWidth - 40f, screenWidth - 40f)
            linesMoveAnimator.duration = 1800
            linesMoveAnimator.interpolator = LinearInterpolator()
            linesMoveAnimator.repeatCount = ValueAnimator.INFINITE
            linesMoveAnimator.start()

            // 3. Flicker/Air Effect (Scale and Alpha pulsing for lines)
            fun animateLine(view: View, baseWidth: Int) {
                val anim = ValueAnimator.ofInt(baseWidth, baseWidth + 30)
                anim.duration = 150
                anim.repeatCount = ValueAnimator.INFINITE
                anim.repeatMode = ValueAnimator.REVERSE
                anim.addUpdateListener {
                    val params = view.layoutParams
                    params.width = it.animatedValue as Int
                    view.layoutParams = params
                    view.alpha = (0.3f + (Math.random() * 0.5f)).toFloat()
                }
                anim.start()
            }

            animateLine(line1, 40.dpToPx())
            animateLine(line2, 60.dpToPx())
            animateLine(line3, 35.dpToPx())

            // 4. Engine Vibration (Bumpy road effect)
            val vibrateAnimator = ObjectAnimator.ofFloat(ivTruck, "translationY", -3f, 3f)
            vibrateAnimator.duration = 80
            vibrateAnimator.repeatCount = ValueAnimator.INFINITE
            vibrateAnimator.repeatMode = ValueAnimator.REVERSE
            vibrateAnimator.start()
        }

        // Minimum splash time to show animations
        Handler(Looper.getMainLooper()).postDelayed({
            isMinTimeDone = true
            checkIfReadyToProceed()
        }, 2500)

        performNetworkCheck()
    }

    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }

    private fun performNetworkCheck() {
        RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
            override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                isNetworkCheckDone = true
                checkIfReadyToProceed()
            }

            override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {
                isNetworkCheckDone = true
                checkIfReadyToProceed()
            }
        })
    }

    private fun checkIfReadyToProceed() {
        if (isMinTimeDone && isNetworkCheckDone) {
            proceedToNextScreen()
        }
    }

    private fun proceedToNextScreen() {
        if (sessionManager.isLoggedIn()) {
            navigateToDashboard(sessionManager.getUser()?.role)
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
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
            
            // If GPS is off, we must send them to MainActivity so they can try again once enabled
            val loginIntent = Intent(this, MainActivity::class.java)
            startActivity(loginIntent)
            finish()
            return
        }

        val intent = when (role?.lowercase()) {
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "resident" -> Intent(this, ResidentDashboardActivity::class.java)
            "driver" -> Intent(this, DriverDashboardActivity::class.java)
            else -> {
                CustomNotification.showTopNotification(this, "Unknown role: $role")
                Intent(this, MainActivity::class.java)
            }
        }
        startActivity(intent)
        finish()
    }
}
