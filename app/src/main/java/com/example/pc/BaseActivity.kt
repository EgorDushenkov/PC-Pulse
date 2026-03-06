package com.example.pc

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

abstract class BaseActivity : AppCompatActivity() {

    protected var currentApi: ApiService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme()
        super.onCreate(savedInstanceState)
        
        intent.getStringExtra("DEVICE_IP")?.let {
            currentApi = RetrofitClient.getClient(it)
        }
    }

    protected fun applyAppTheme() {
        val prefs = getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
        val theme = prefs.getString("APP_THEME", "PURPLE")
        val themeRes = when (theme) {
            "TURQUOISE" -> R.style.AppTheme_Turquoise
            "ORANGE" -> R.style.AppTheme_Orange
            "GREEN" -> R.style.AppTheme_Green
            else -> R.style.AppTheme_Purple
        }
        setTheme(themeRes)
    }

    protected fun showScreenshotDialog() {
        Toast.makeText(this, "Taking Screenshot...", Toast.LENGTH_SHORT).show()
        currentApi?.getScreenshot()?.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    val bytes = response.body()!!.bytes()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val view = layoutInflater.inflate(R.layout.dialog_screenshot, null)
                    view.findViewById<ImageView>(R.id.screenshotImage).setImageBitmap(bitmap)
                    AlertDialog.Builder(this@BaseActivity).setTitle("PC Screenshot").setView(view).setPositiveButton("Close", null).show()
                }
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
        })
    }

    protected fun sendMixerVolume(appName: String, volume: Int) {
        currentApi?.setMixerVolume(appName, volume)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {}
            override fun onFailure(call: Call<String>, t: Throwable) {}
        })
    }

    protected fun killProcess(pid: Int, onComplete: () -> Unit = {}) {
        currentApi?.killProcess(pid)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) { onComplete() }
            override fun onFailure(call: Call<String>, t: Throwable) {}
        })
    }

    protected fun sendShutdownCommand() {
        showPowerActionDialog("выключить", "Выключение") {
            currentApi?.shutdownPC()?.enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {}
                override fun onFailure(call: Call<String>, t: Throwable) {}
            })
        }
    }

    protected fun sendSleepCommand() {
        showPowerActionDialog("отправить в спящий режим", "Сон") {
            currentApi?.sleepPC()?.enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {}
                override fun onFailure(call: Call<String>, t: Throwable) {}
            })
        }
    }

    private fun showPowerActionDialog(actionName: String, title: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage("Вы уверены, что хотите $actionName ПК?").setPositiveButton("Да") { _, _ -> onConfirm() }.setNegativeButton("Нет", null).show()
    }
}