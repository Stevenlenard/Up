package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import android.graphics.Color
import com.example.myapplication.utils.PredictionEngine
import com.github.mikephil.charting.charts.*
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.database.*
import com.example.myapplication.models.ComplaintsResponse
import com.example.myapplication.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.util.Log
import android.app.DatePickerDialog
import android.net.Uri
import java.text.SimpleDateFormat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.util.*

class AnalyticsActivity : AppCompatActivity() {

    private val dbUrl = "https://garbagesis-78d39-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val database = FirebaseDatabase.getInstance(dbUrl)
    
    private var selectedPurok: String? = null
    private var selectedDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val purokList = arrayOf(
        "Purok 2", "Purok 3", "Purok 4", "Dos Riles", "Sentro", 
        "San Isidro", "Paraiso", "Riverside", "Kalaw Street", 
        "Home Subdivision", "Tanco Road / Ayala Highway", "Brixton Area"
    )

    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() { refreshAllData(); refreshHandler.postDelayed(this, 10000) }
    }

    private fun refreshAllData() {
        fetchDataForCharts()
        fetchComplaintsForChart()
        calculateRouteEfficiency()
        updateDynamicETAs()
        fetchNotificationStats()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.analytics_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
            overridePendingTransition(0, 0); finish()
        }

        findViewById<MaterialButton>(R.id.btn_export).setOnClickListener { showExportModal() }
        findViewById<MaterialButton>(R.id.btn_select_purok_global).setOnClickListener { showPurokSelectionModal() }
        findViewById<MaterialButton>(R.id.btn_select_date).setOnClickListener { showDatePicker() }
        findViewById<android.view.View>(R.id.card_purok_coverage).setOnClickListener { showFullCoverageModal() }

        setupCharts()
        refreshAllData()
        setupBottomNavigation()
    }

    private fun showFullCoverageModal() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_full_chart, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        
        val fullChart = view.findViewById<HorizontalBarChart>(R.id.full_bar_chart)
        val tvSummary = view.findViewById<TextView>(R.id.tv_modal_summary)
        
        view.findViewById<android.view.View>(R.id.btn_close_modal).setOnClickListener { dialog.dismiss() }
        view.findViewById<android.view.View>(R.id.btn_close_full).setOnClickListener { dialog.dismiss() }
        
        fullChart.apply {
            description.isEnabled = false; setDrawGridBackground(false)
            
            // Use setExtraOffsets to provide global padding for axis and legend
            setExtraOffsets(10f, 40f, 10f, 60f) 
            
            xAxis.apply { 
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                granularity = 1f; labelCount = 12
                valueFormatter = IndexAxisValueFormatter(purokList)
                textSize = 8f // Slightly smaller text
                setDrawAxisLine(true)
            }
            axisLeft.apply { 
                axisMinimum = -40f; axisMaximum = 40f; setDrawGridLines(true)
                setLabelCount(5, true) 
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() { override fun getFormattedValue(v: Float): String = Math.abs(v).toInt().toString() }
                textSize = 8f // Slightly smaller numbers
                setPosition(com.github.mikephil.charting.components.YAxis.YAxisLabelPosition.OUTSIDE_CHART)
            }
            axisRight.isEnabled = false
            minOffset = 0f
            
            // Refined ViewPort to balance text space and chart area
            setViewPortOffsets(180f, 60f, 40f, 80f) 
            
            legend.apply {
                isEnabled = true
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                yOffset = 5f 
                xEntrySpace = 8f // Tighter indicator spacing
                formSize = 7f // Smaller boxes
                textSize = 8f // Smaller text
                isWordWrapEnabled = false
            }
        }

        val frequencyMap = mutableMapOf<String, Int>().apply { purokList.forEach { put(it, 0) } }
        val complaintMap = mutableMapOf<String, Int>().apply { purokList.forEach { put(it, 0) } }
        val thirtyDaysAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -30); return@apply }.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.time)

        database.getReference("collection_logs").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { log ->
                    val date = log.child("date").getValue(String::class.java) ?: ""
                    val zone = log.child("zoneName").getValue(String::class.java) ?: ""
                    if (date >= thirtyDaysAgo && log.child("type").getValue(String::class.java) == "ENTRY" && frequencyMap.containsKey(zone)) frequencyMap[zone] = frequencyMap[zone]!! + 1
                }
                RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
                    override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                        if (response.isSuccessful) {
                            response.body()?.data?.forEach { c -> if (c.purok != null && complaintMap.containsKey(c.purok)) complaintMap[c.purok] = complaintMap[c.purok]!! + 1 }
                            renderBidirectionalChart(fullChart, frequencyMap, complaintMap)
                            
                            // Generate Intelligence Summary for Modal
                            val topPurok = frequencyMap.maxByOrNull { it.value }?.key
                            val worstPurok = complaintMap.maxByOrNull { it.value }?.key
                            val totalC = complaintMap.values.sum()
                            
                            val summary = StringBuilder("System Analysis (Last 30 Days):\n\n")
                            summary.append("• Most Active Area: $topPurok has the highest collection frequency.\n")
                            if (worstPurok != null && complaintMap[worstPurok]!! > 0) {
                                summary.append("• Critical Attention: $worstPurok recorded $totalC total complaints. ")
                                if (frequencyMap[worstPurok]!! < 5) summary.append("Low collection frequency detected in this area.")
                            } else summary.append("• Resident Satisfaction: No major complaints recorded in high-traffic zones.\n")
                            
                            summary.append("\nExplanation of Data & Scale:\n")
                            summary.append("• The center line (0) is the baseline. Purple bars grow left (Visits), Blue bars grow right (Complaints).\n")
                            summary.append("• The 0-40 scale represents the volume over 30 days. A truck visiting daily equals 30; we use 40 to accommodate extra visits or high complaint spikes.\n")
                            
                            summary.append("\nRecommendation: Adjust routes to prioritize $worstPurok if complaints persist.")
                            tvSummary.text = summary.toString()
                        }
                    }
                    override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        dialog.show()
    }

    override fun onResume() { super.onResume(); refreshHandler.post(refreshRunnable) }
    override fun onPause() { super.onPause(); refreshHandler.removeCallbacks(refreshRunnable) }

    private fun showDatePicker() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            c.set(y, m, d); selectedDate = sdf.format(c.time)
            findViewById<MaterialButton>(R.id.btn_select_date).text = if (selectedDate == sdf.format(Date())) "Today" else SimpleDateFormat("MMM dd", Locale.getDefault()).format(c.time)
            refreshAllData()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun calculateRouteEfficiency() {
        database.getReference("truck_locations").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalDist = 0.0
                var totalStops = 0
                var totalStopDurationMs = 0L
                var totalMaeErrorSec = 0.0
                var maeCount = 0

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                for (truck in snapshot.children) {
                    val points = mutableListOf<Triple<Double, Double, Long>>()
                    for (p in truck.child("route_history").children) {
                        val lat = p.child("lat").getValue(Double::class.java) ?: continue
                        val lng = p.child("lng").getValue(Double::class.java) ?: continue
                        val time = p.child("time").value?.toString()?.toLongOrNull() ?: 0L
                        if (time > 0 && sdf.format(Date(time)) == selectedDate) {
                            if (selectedPurok == null || com.example.myapplication.utils.PurokManager.getZoneAt(lat, lng)?.name == selectedPurok) {
                                points.add(Triple(lat, lng, time))
                            }
                        }
                    }

                    if (points.size < 2) continue
                    val historicalForPrediction = mutableListOf<Pair<Double, Double>>()

                    for (i in 0 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i+1]
                        val res = FloatArray(1)
                        android.location.Location.distanceBetween(p1.first, p1.second, p2.first, p2.second, res)
                        val dist = res[0].toDouble()
                        totalDist += dist

                        val timeDiffMs = p2.third - p1.third
                        val actualSec = timeDiffMs / 1000.0

                        if (timeDiffMs in 180000..600000) {
                            totalStops++
                            totalStopDurationMs += timeDiffMs
                        }

                        if (dist > 10.0) {
                            val predictedSec = PredictionEngine.predictArrivalTime(dist, historicalForPrediction)
                            totalMaeErrorSec += Math.abs(actualSec - predictedSec)
                            maeCount++
                            historicalForPrediction.add(dist to actualSec)
                        }
                    }
                }
                findViewById<TextView>(R.id.tv_distance_covered).text = String.format("%.1f km", totalDist / 1000.0)
                findViewById<TextView>(R.id.tv_stops_per_route).text = "$totalStops stops"
                findViewById<TextView>(R.id.tv_avg_collection_time).text = String.format("%.1f hours", totalStopDurationMs / 3600000.0)
                findViewById<TextView>(R.id.tv_prediction_error).text = if (maeCount > 0) String.format("%.1fs", totalMaeErrorSec / maeCount) else "0.0s"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private var currentComplaintsCount = 0
    private fun fetchComplaintsForChart() {
        RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
            override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()?.data ?: emptyList()
                    val counts = mutableMapOf("Pending" to 0, "In Progress" to 0, "Resolved" to 0)
                    var total = 0
                    for (c in list) {
                        val st = c.status.uppercase().replace("_", " ")
                        val persistent = st == "PENDING" || st == "IN PROGRESS"
                        if ((selectedPurok == null || c.purok == selectedPurok) && (persistent || c.createdAt.startsWith(selectedDate))) {
                            total++; when(st){ "PENDING"->counts["Pending"]=counts["Pending"]!!+1; "IN PROGRESS"->counts["In Progress"]=counts["In Progress"]!!+1; "RESOLVED"->counts["Resolved"]=counts["Resolved"]!!+1 }
                        }
                    }
                    currentComplaintsCount = total
                    val chart = findViewById<PieChart>(R.id.chart_complaints_status)
                    val msg = findViewById<TextView>(R.id.tv_no_complaints_msg)
                    if (total > 0) { chart.visibility = android.view.View.VISIBLE; msg.visibility = android.view.View.GONE; updatePieChart(chart, counts, "Complaints Status") }
                    else { chart.visibility = android.view.View.INVISIBLE; msg.visibility = android.view.View.VISIBLE; msg.text = "No complaints recorded." }
                    calculatePerformanceMetrics(list); generateSmartInsights()
                }
            }
            override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {}
        })
    }

    private fun generateSmartInsights() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrowDate = sdf.format(cal.time)
        
        // 🧪 ML-BASED WASTE PREDICTION
        val tomorrowVolume = PredictionEngine.predictWasteVolume(tomorrowDate, selectedPurok, null)
        findViewById<TextView>(R.id.tv_waste_tomorrow).text = String.format("%.0f kg", tomorrowVolume)

        var weeklyTotal = 0.0; val tempCal = cal.clone() as Calendar
        for (i in 0..6) { weeklyTotal += PredictionEngine.predictWasteVolume(sdf.format(tempCal.time), selectedPurok, null); tempCal.add(Calendar.DAY_OF_YEAR, 1) }
        findViewById<TextView>(R.id.tv_waste_week).text = String.format("%.0f kg", weeklyTotal)
        
        val cap = PredictionEngine.getTruckCapacityKilos()
        findViewById<TextView>(R.id.tv_truck_capacity).text = String.format("%.0f kg", cap)
        findViewById<TextView>(R.id.tv_waste_volume_title).text = if (selectedPurok != null) "Waste Prediction: $selectedPurok" else "Waste Volume Prediction"

        val insights = PredictionEngine.generateInsights(tomorrowVolume, cap, PredictionEngine.getHolidayMultiplier(tomorrowDate) > 1.0, selectedPurok, currentComplaintsCount)
        findViewById<TextView>(R.id.tv_rec_1).text = "• ${insights.getOrNull(0) ?: "Normal volume expected."}"
        findViewById<TextView>(R.id.tv_rec_2).text = "• ${insights.getOrNull(1) ?: "Optimal route efficiency expected."}"
        findViewById<TextView>(R.id.tv_rec_3).text = "• Note: Volume adjusted for holidays & area size."
    }

    private fun updateDynamicETAs() {
        if (selectedDate != SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) {
            findViewById<TextView>(R.id.tv_eta_1_label).text = "Historical Mode:"; findViewById<TextView>(R.id.tv_eta_1_value).text = "N/A"; return
        }
        database.getReference("truck_locations").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val active = snapshot.children.filter { it.child("status").getValue(String::class.java) == "active" }
                if (active.isEmpty()) { findViewById<TextView>(R.id.tv_eta_1_value).text = "No Active Truck"; return }
                
                if (selectedPurok != null) {
                    val zone = com.example.myapplication.utils.PurokManager.purokZones.find { it.name == selectedPurok }
                    if (zone != null) {
                        val closest = active.minByOrNull { t -> val res = FloatArray(1); android.location.Location.distanceBetween(t.child("latitude").getValue(Double::class.java) ?: 0.0, t.child("longitude").getValue(Double::class.java) ?: 0.0, zone.latitude, zone.longitude, res); res[0] }
                        val res = FloatArray(1); android.location.Location.distanceBetween(closest?.child("latitude")?.getValue(Double::class.java) ?: 0.0, closest?.child("longitude")?.getValue(Double::class.java) ?: 0.0, zone.latitude, zone.longitude, res)
                        val etaSec = PredictionEngine.predictArrivalTime(res[0].toDouble(), emptyList())
                        findViewById<TextView>(R.id.tv_eta_1_label).text = "Distance:"; findViewById<TextView>(R.id.tv_eta_1_value).text = String.format("%.1f km", res[0] / 1000.0)
                        findViewById<TextView>(R.id.tv_eta_2_label).text = "Est. Arrival Time:"; findViewById<TextView>(R.id.tv_eta_2_value).text = "${(etaSec / 60).toInt()} mins"
                    }
                } else {
                    findViewById<TextView>(R.id.tv_eta_1_label).text = "Next Truck:"; findViewById<TextView>(R.id.tv_eta_1_value).text = "Calculating live..."
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun calculatePerformanceMetrics(complaints: List<com.example.myapplication.models.Complaint>) {
        if (complaints.isEmpty()) return
        val res = complaints.count { it.status.uppercase() == "RESOLVED" }
        findViewById<TextView>(R.id.tv_resolution_rate).text = String.format("%.1f%%", (res.toDouble() / complaints.size) * 100)
    }

    private fun fetchNotificationStats() {
        database.getReference("notification_logs").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var total = 0; var w = 0; var e = 0
                for (log in snapshot.children) {
                    if (log.child("date").getValue(String::class.java) == selectedDate && (selectedPurok == null || log.child("zoneName").getValue(String::class.java) == selectedPurok)) {
                        total++; when (log.child("type").getValue(String::class.java)) { "ARRIVAL_WARNING" -> w++; "ENTERING" -> e++ }
                    }
                }
                findViewById<TextView>(R.id.tv_notifications_sent).text = "$total sent"; findViewById<TextView>(R.id.tv_notification_breakdown).text = "• Warning: $w | • Entering: $e"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showPurokSelectionModal() {
        AlertDialog.Builder(this).setTitle("Global Filter: Select Area").setItems(purokList) { _, which ->
            selectedPurok = purokList[which]; findViewById<MaterialButton>(R.id.btn_select_purok_global).text = selectedPurok
            findViewById<TextView>(R.id.tv_selected_purok_name).text = "Viewing: $selectedPurok"; refreshAllData()
        }.setNeutralButton("Show All Areas") { _, _ ->
            selectedPurok = null; findViewById<MaterialButton>(R.id.btn_select_purok_global).text = "All Areas"
            findViewById<TextView>(R.id.tv_selected_purok_name).text = "Viewing: All Areas"; refreshAllData()
        }.show()
    }

    private fun setupCharts() {
        findViewById<PieChart>(R.id.chart_truck_status).apply { description.isEnabled = false; isDrawHoleEnabled = true; setHoleColor(Color.TRANSPARENT); legend.isEnabled = true }
        findViewById<PieChart>(R.id.chart_complaints_status).apply { description.isEnabled = false; isDrawHoleEnabled = true; setHoleColor(Color.TRANSPARENT); legend.isEnabled = true }
        findViewById<HorizontalBarChart>(R.id.chart_purok_coverage).apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawValueAboveBar(true)
            
            // Global padding to prevent edge clipping
            setExtraOffsets(10f, 40f, 10f, 60f)

            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelCount = purokList.size
                valueFormatter = IndexAxisValueFormatter(purokList)
                textSize = 8f // Reduced for better fit
                setDrawAxisLine(true)
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = -40f 
                axisMaximum = 40f
                setLabelCount(5, true) 
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = Math.abs(value).toInt().toString()
                }
                textSize = 8f // Reduced
                setPosition(com.github.mikephil.charting.components.YAxis.YAxisLabelPosition.OUTSIDE_CHART)
            }
            axisRight.isEnabled = false
            legend.isEnabled = true
            minOffset = 0f
            
            // Balanced offsets: 180f for names is enough for most names
            setViewPortOffsets(180f, 60f, 40f, 80f) 
            
            legend.apply {
                isEnabled = true
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                yOffset = 5f
                xEntrySpace = 8f
                formSize = 7f
                textSize = 8f
                isWordWrapEnabled = false
            }
        }
    }

    private fun fetchDataForCharts() {
        database.getReference("collection_logs").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val visitedToday = snapshot.children.filter { it.child("date").getValue(String::class.java) == selectedDate }.mapNotNull { it.child("zoneName").getValue(String::class.java) }.toSet()
                findViewById<TextView>(R.id.tv_analytics_coverage).text = "${(visitedToday.size * 100 / 12)}%"; findViewById<TextView>(R.id.tv_analytics_routes_done).text = "${visitedToday.size}/12"
                
                // Fetch All Data for the Bidirectional Chart
                fetchBidirectionalData()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        database.getReference("truck_locations").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val counts = mutableMapOf("Active" to 0, "Idle" to 0, "Full" to 0, "Completed" to 0)
                snapshot.children.forEach { val st = it.child("status").getValue(String::class.java)?.lowercase(); when(st){ "active"->counts["Active"]=counts["Active"]!!+1; "idle"->counts["Idle"]=counts["Idle"]!!+1; "full"->counts["Full"]=counts["Full"]!!+1; "completed"->counts["Completed"]=counts["Completed"]!!+1 } }
                updatePieChart(findViewById(R.id.chart_truck_status), counts, "Truck Status")
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchBidirectionalData() {
        val frequencyMap = mutableMapOf<String, Int>().apply { purokList.forEach { put(it, 0) } }
        val complaintMap = mutableMapOf<String, Int>().apply { purokList.forEach { put(it, 0) } }

        // 1. Get Collection Frequency (Last 30 Days)
        val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -30)
        val thirtyDaysAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        database.getReference("collection_logs").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { log ->
                    val date = log.child("date").getValue(String::class.java) ?: ""
                    val zone = log.child("zoneName").getValue(String::class.java) ?: ""
                    val type = log.child("type").getValue(String::class.java) ?: ""
                    if (date >= thirtyDaysAgo && type == "ENTRY" && frequencyMap.containsKey(zone)) {
                        frequencyMap[zone] = frequencyMap[zone]!! + 1
                    }
                }

                // 2. Get Complaints count per Purok
                RetrofitClient.instance.getComplaints().enqueue(object : Callback<ComplaintsResponse> {
                    override fun onResponse(call: Call<ComplaintsResponse>, response: Response<ComplaintsResponse>) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            response.body()?.data?.forEach { complaint ->
                                val p = complaint.purok
                                if (p != null && complaintMap.containsKey(p)) {
                                    complaintMap[p] = complaintMap[p]!! + 1
                                }
                            }
                            renderBidirectionalChart(findViewById(R.id.chart_purok_coverage), frequencyMap, complaintMap)
                        }
                    }
                    override fun onFailure(call: Call<ComplaintsResponse>, t: Throwable) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun renderBidirectionalChart(chart: HorizontalBarChart, freq: Map<String, Int>, complaints: Map<String, Int>) {
        val freqEntries = mutableListOf<BarEntry>()
        val complaintEntries = mutableListOf<BarEntry>()

        purokList.forEachIndexed { index, name ->
            freqEntries.add(BarEntry(index.toFloat(), freq[name]?.toFloat()?.times(-1) ?: 0f))
            complaintEntries.add(BarEntry(index.toFloat(), complaints[name]?.toFloat() ?: 0f))
        }

        val freqSet = BarDataSet(freqEntries, "Collection Frequency (30d)").apply { color = Color.parseColor("#9C27B0"); valueTextSize = 10f; valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() { override fun getFormattedValue(v: Float): String = Math.abs(v).toInt().toString() } }
        val complaintSet = BarDataSet(complaintEntries, "Resident Complaints").apply { color = Color.parseColor("#2196F3"); valueTextSize = 10f; valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() { override fun getFormattedValue(v: Float): String = v.toInt().toString() } }

        chart.data = BarData(freqSet, complaintSet).apply { barWidth = 0.4f }
        chart.invalidate()
    }

    private fun updatePieChart(chart: PieChart, data: Map<String, Int>, label: String) {
        val entries = data.filter { it.value > 0 }.map { PieEntry(it.value.toFloat(), it.key) }
        val dataSet = PieDataSet(entries, label).apply {
            if (label.contains("Complaints")) {
                colors = listOf(Color.parseColor("#F9A825"), Color.parseColor("#1E88E5"), Color.parseColor("#43A047"))
                yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE; xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE; valueLineColor = Color.BLACK; valueLinePart1Length = 0.4f; valueLinePart2Length = 0.4f
            } else colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#FFC107"), Color.parseColor("#F44336"), Color.parseColor("#2196F3"))
            valueTextSize = 12f; valueTextColor = Color.BLACK
        }
        chart.apply { this.data = PieData(dataSet); setEntryLabelColor(Color.BLACK); setExtraOffsets(25f, 5f, 25f, 5f); invalidate() }
    }

    private fun showExportModal() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_export_report, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        val typeSpinner = view.findViewById<AutoCompleteTextView>(R.id.spinner_report_type)
        typeSpinner.setAdapter(ArrayAdapter(this, R.layout.dropdown_item, arrayOf("All Reports", "Truck Performance", "Complaints Summary", "Route Efficiency", "Purok Coverage")))
        val formatSpinner = view.findViewById<AutoCompleteTextView>(R.id.spinner_format)
        formatSpinner.setAdapter(ArrayAdapter(this, R.layout.dropdown_item, arrayOf("PDF Document (.pdf)", "Excel Spreadsheet (.xlsx)")))
        val etStart = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_start_date)
        val etEnd = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_end_date)
        etStart.setText(selectedDate); etEnd.setText(selectedDate)
        
        val setDate = { et: android.widget.EditText ->
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d -> cal.set(y, m, d); et.setText(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        etStart.setOnClickListener { setDate(etStart) }; etEnd.setOnClickListener { setDate(etEnd) }

        view.findViewById<MaterialButton>(R.id.btn_export_now).setOnClickListener {
            val fmt = formatSpinner.text.toString()
            if (fmt.contains(".pdf")) generateNativePDF(typeSpinner.text.toString(), etStart.text.toString(), etEnd.text.toString())
            else {
                val url = "http://192.168.254.106/Asia-repo1-main/backend/export_report.php?type=${typeSpinner.text}&format=${if(fmt.contains(".xlsx"))"xls" else "csv"}&start_date=${etStart.text}&end_date=${etEnd.text}&res_rate=${findViewById<TextView>(R.id.tv_resolution_rate).text}&avg_time=${findViewById<TextView>(R.id.tv_performance_avg_response).text}&tomorrow=${findViewById<TextView>(R.id.tv_waste_tomorrow).text}"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun generateNativePDF(reportType: String, start: String, end: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.report_pdf_template, null)
        view.findViewById<TextView>(R.id.tv_pdf_report_type).text = "Report Type: $reportType"
        view.findViewById<TextView>(R.id.tv_pdf_period).text = "Period: $start to $end"
        view.findViewById<TextView>(R.id.tv_pdf_prediction_tomorrow).text = findViewById<TextView>(R.id.tv_waste_tomorrow).text
        view.findViewById<TextView>(R.id.tv_pdf_prediction_week).text = findViewById<TextView>(R.id.tv_waste_week).text
        view.findViewById<TextView>(R.id.tv_pdf_avg_response).text = findViewById<TextView>(R.id.tv_performance_avg_response).text
        view.findViewById<TextView>(R.id.tv_pdf_resolution_rate).text = findViewById<TextView>(R.id.tv_resolution_rate).text
        
        // Populate Route Efficiency in PDF
        view.findViewById<TextView>(R.id.tv_pdf_avg_collection_time).text = findViewById<TextView>(R.id.tv_avg_collection_time).text
        view.findViewById<TextView>(R.id.tv_pdf_stops).text = findViewById<TextView>(R.id.tv_stops_per_route).text
        view.findViewById<TextView>(R.id.tv_pdf_distance).text = findViewById<TextView>(R.id.tv_distance_covered).text
        view.findViewById<TextView>(R.id.tv_pdf_mae).text = findViewById<TextView>(R.id.tv_prediction_error).text

        view.findViewById<TextView>(R.id.tv_pdf_intelligent_summary).text = generateIntelligentSummary()
        
        // --- PDF FILTERING LOGIC ---
        // Hide all sections initially if not "All Reports"
        if (reportType != "All Reports") {
            val sections = listOf(
                R.id.tv_pdf_section_1_title, R.id.layout_pdf_charts_row_1, R.id.layout_pdf_purok_coverage,
                R.id.tv_pdf_section_2_title, R.id.layout_pdf_performance,
                R.id.tv_pdf_section_3_title, R.id.layout_pdf_route_efficiency
            )
            sections.forEach { view.findViewById<android.view.View>(it)?.visibility = android.view.View.GONE }
            
            // Re-show relevant rows/sections based on type
            when (reportType) {
                "Truck Performance" -> {
                    view.findViewById<android.view.View>(R.id.tv_pdf_section_1_title).visibility = android.view.View.VISIBLE
                    view.findViewById<android.view.View>(R.id.layout_pdf_charts_row_1).visibility = android.view.View.VISIBLE
                    view.findViewById<android.view.View>(R.id.layout_pdf_complaints).visibility = android.view.View.GONE // Only truck status
                }
                "Complaints Summary" -> {
                    view.findViewById<android.view.View>(R.id.tv_pdf_section_1_title).visibility = android.view.View.VISIBLE
                    view.findViewById<android.view.View>(R.id.layout_pdf_charts_row_1).visibility = android.view.View.VISIBLE
                    view.findViewById<android.view.View>(R.id.layout_pdf_truck_status).visibility = android.view.View.GONE // Only complaints
                    view.findViewById<android.view.View>(R.id.tv_pdf_section_2_title).visibility = android.view.View.VISIBLE
                    view.findViewById<android.view.View>(R.id.layout_pdf_performance).visibility = android.view.View.VISIBLE
                    // Hide non-complaint performance rows
                    view.findViewById<android.view.View>(R.id.row_pdf_prediction_tomorrow).visibility = android.view.View.GONE
                    view.findViewById<android.view.View>(R.id.row_pdf_prediction_week).visibility = android.view.View.GONE
                }
                "Route Efficiency" -> {
                    view.findViewById<android.view.View>(R.id.tv_pdf_section_3_title).visibility = android.view.View.VISIBLE
                    view.findViewById<android.view.View>(R.id.layout_pdf_route_efficiency).visibility = android.view.View.VISIBLE
                }
                "Purok Coverage" -> {
                    view.findViewById<android.view.View>(R.id.tv_pdf_section_1_title).visibility = android.view.View.VISIBLE
                    view.findViewById<android.view.View>(R.id.layout_pdf_purok_coverage).visibility = android.view.View.VISIBLE
                }
            }
        }

        // Populate Charts in PDF
        val truckChart = findViewById<PieChart>(R.id.chart_truck_status)
        view.findViewById<ImageView>(R.id.img_chart_truck).setImageBitmap(truckChart.chartBitmap)

        val complaintsChart = findViewById<PieChart>(R.id.chart_complaints_status)
        if (complaintsChart.visibility == android.view.View.VISIBLE) {
            view.findViewById<ImageView>(R.id.img_chart_complaints).setImageBitmap(complaintsChart.chartBitmap)
        } else {
            view.findViewById<TextView>(R.id.tv_pdf_no_complaints_detail).visibility = android.view.View.VISIBLE
        }

        val coverageChart = findViewById<HorizontalBarChart>(R.id.chart_purok_coverage)
        
        // 1. Temporarily optimize for PDF (ensure offsets are generous for the captured bitmap)
        val oldTextSize = coverageChart.xAxis.textSize
        coverageChart.xAxis.textSize = 9f
        coverageChart.axisLeft.textSize = 9f
        coverageChart.legend.textSize = 9f
        coverageChart.legend.formSize = 8f
        
        // CRITICAL FIX: Adjust offsets to fit everything in a smaller image without cutting
        coverageChart.setExtraOffsets(0f, 20f, 10f, 30f)
        coverageChart.setViewPortOffsets(160f, 40f, 30f, 60f)
        
        // 2. Capture the high-quality bitmap
        view.findViewById<ImageView>(R.id.img_chart_coverage).setImageBitmap(coverageChart.chartBitmap)
        
        // 3. Restore UI text sizes for the live screen
        coverageChart.xAxis.textSize = oldTextSize
        coverageChart.axisLeft.textSize = oldTextSize
        coverageChart.legend.textSize = oldTextSize
        coverageChart.legend.formSize = 8f
        coverageChart.setViewPortOffsets(180f, 60f, 40f, 80f)

        val width = 595; view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val doc = PdfDocument(); val page = doc.startPage(PdfDocument.PageInfo.Builder(width, view.measuredHeight, 1).create())
        view.draw(page.canvas); doc.finishPage(page)
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Professional_Report_${System.currentTimeMillis()}.pdf")
        try { doc.writeTo(FileOutputStream(file)); Toast.makeText(this, "PDF Saved", Toast.LENGTH_LONG).show(); startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.fromFile(file), "application/pdf"); flags = Intent.FLAG_ACTIVITY_CLEAR_TOP }) }
        catch (e: Exception) { e.printStackTrace() } finally { doc.close() }
    }

    private fun generateIntelligentSummary(): String {
        val cov = findViewById<TextView>(R.id.tv_analytics_coverage).text; val res = findViewById<TextView>(R.id.tv_resolution_rate).text
        val tomorrow = findViewById<TextView>(R.id.tv_waste_tomorrow).text.toString().replace(" kg", "").toDoubleOrNull() ?: 0.0
        val sb = StringBuilder("Executive Overview: Achieved $cov coverage with a $res resolution rate. ")
        if (tomorrow > 5000) sb.append("Critical Alert: Predicted volume ($tomorrow kg) exceeds truck capacity.")
        return sb.toString()
    }

    private fun setupBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.bottom_navigation); nav.selectedItemId = R.id.nav_reports
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_monitor -> { startActivity(Intent(this, AdminDashboardActivity::class.java)); finish(); true }
                R.id.nav_track -> { startActivity(Intent(this, TrackTrucksActivity::class.java)); finish(); true }
                R.id.nav_reports -> true
                R.id.nav_complaints -> { startActivity(Intent(this, ComplaintsActivity::class.java)); finish(); true }
                R.id.nav_settings -> { startActivity(Intent(this, AdminSettingsActivity::class.java)); finish(); true }
                else -> false
            }
        }
    }
}
