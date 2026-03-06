package com.example.pc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btn_theme_purple).setOnClickListener { saveTheme("PURPLE") }
        findViewById<Button>(R.id.btn_theme_turquoise).setOnClickListener { saveTheme("TURQUOISE") }
        findViewById<Button>(R.id.btn_theme_orange).setOnClickListener { saveTheme("ORANGE") }
        findViewById<Button>(R.id.btn_theme_green).setOnClickListener { saveTheme("GREEN") }
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