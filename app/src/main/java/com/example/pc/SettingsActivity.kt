package com.example.pc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Установка темы перед super.onCreate
        val prefs = getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
        val theme = prefs.getString("APP_THEME", "PURPLE")
        if (theme == "TURQUOISE") {
            setTheme(R.style.AppTheme_Turquoise)
        } else {
            setTheme(R.style.AppTheme_Purple)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btn_theme_purple).setOnClickListener {
            saveTheme("PURPLE")
        }

        findViewById<Button>(R.id.btn_theme_turquoise).setOnClickListener {
            saveTheme("TURQUOISE")
        }
    }

    private fun saveTheme(theme: String) {
        val prefs = getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
        prefs.edit().putString("APP_THEME", theme).apply()
        
        // Перезапуск приложения для применения темы ко всем экранам
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
}