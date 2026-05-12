package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.register_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.card_resident).setOnClickListener {
            val intent = Intent(this, ResidentRegisterActivity::class.java)
            startActivity(intent)
        }

        findViewById<View>(R.id.card_driver).setOnClickListener {
            val intent = Intent(this, DriverRegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
