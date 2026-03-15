package com.example.pc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
        val vibrationSwitch = findViewById<SwitchCompat>(R.id.switch_vibration)
        vibrationSwitch.isChecked = prefs.getBoolean("VIBRATION_ENABLED", true)
        
        vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("VIBRATION_ENABLED", isChecked).apply()
            if (isChecked) vibrate()
        }

        findViewById<Button>(R.id.btn_theme_purple).setOnClickListener { 
            vibrate()
            saveTheme("PURPLE") 
        }
        findViewById<Button>(R.id.btn_theme_turquoise).setOnClickListener { 
            vibrate()
            saveTheme("TURQUOISE") 
        }
        findViewById<Button>(R.id.btn_theme_orange).setOnClickListener { 
            vibrate()
            saveTheme("ORANGE") 
        }
        findViewById<Button>(R.id.btn_theme_green).setOnClickListener { 
            vibrate()
            saveTheme("GREEN") 
        }
    }

    private fun saveTheme(theme: String) {
        getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
            .edit()
            .putString("APP_THEME", theme)
            .apply()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
    }
}