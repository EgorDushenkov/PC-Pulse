package com.example.pc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

data class Device(
    val ipAddress: String,
    var pcName: String = "Загрузка...",
    var status: String = "Проверка...",
    var quickStats: String = "CPU: --% | GPU: --%",
    var isOnline: Boolean = false
)

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var fabSettings: FloatingActionButton

    private lateinit var deviceAdapter: DeviceAdapter
    private val devices = mutableListOf<Device>()
    private var currentDeviceIndex = 0

    private val runnable = object : Runnable {
        override fun run() {
            if (devices.isNotEmpty()) {
                fetchStatsForDevice(currentDeviceIndex)
                currentDeviceIndex = (currentDeviceIndex + 1) % devices.size
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Установка темы
        val prefs = getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
        val theme = prefs.getString("APP_THEME", "PURPLE")
        if (theme == "TURQUOISE") {
            setTheme(R.style.AppTheme_Turquoise)
        } else {
            setTheme(R.style.AppTheme_Purple)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        fabAdd = findViewById(R.id.fab_add)
        fabSettings = findViewById(R.id.fab_settings)

        setupRecyclerView()
        loadDevices()

        fabAdd.setOnClickListener {
            showAddDeviceDialog()
        }
        
        fabSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        handler.post(runnable)
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter(devices,
            onItemClick = { device ->
                if (device.isOnline) {
                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.putExtra("DEVICE_IP", device.ipAddress)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Устройство не в сети", Toast.LENGTH_SHORT).show()
                }
            },
            onItemLongClick = { device ->
                showDeleteDeviceDialog(device)
            }
        )
        devicesRecyclerView.adapter = deviceAdapter
        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun showAddDeviceDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Добавить устройство")
        val input = EditText(this)
        input.hint = "Введите IP (например: 192.168.1.23)"
        input.setSingleLine()
        builder.setView(input)
        builder.setPositiveButton("Сохранить") { _, _ ->
            val ip = input.text.toString().trim()
            if (ip.isNotEmpty() && devices.none { it.ipAddress == ip }) {
                val newDevice = Device(ip)
                devices.add(newDevice)
                deviceAdapter.notifyItemInserted(devices.size - 1)
                saveDevices()
            }
        }
        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun showDeleteDeviceDialog(device: Device) {
        AlertDialog.Builder(this)
            .setTitle("Удалить устройство?")
            .setMessage("Вы уверены?")
            .setPositiveButton("Удалить") { _, _ ->
                val index = devices.indexOf(device)
                if (index != -1) {
                    devices.removeAt(index)
                    deviceAdapter.notifyItemRemoved(index)
                    saveDevices()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun fetchStatsForDevice(index: Int) {
        val device = devices[index]
        val api = RetrofitClient.getClient(device.ipAddress)
        api.getStats().enqueue(object : Callback<PCStats> {
            override fun onResponse(call: Call<PCStats>, response: Response<PCStats>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        device.isOnline = true
                        device.pcName = it.pc_name
                        device.status = "Online | ${it.time}"
                        device.quickStats = "CPU: ${it.cpu.usage}% | GPU: ${it.gpu.getOrNull(0)?.load ?: 0}%"
                    }
                } else {
                    device.isOnline = false
                }
                deviceAdapter.notifyItemChanged(index)
            }
            override fun onFailure(call: Call<PCStats>, t: Throwable) {
                device.isOnline = false
                deviceAdapter.notifyItemChanged(index)
            }
        })
    }

    private fun saveDevices() {
        val prefs = getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("DEVICE_IPS", devices.map { it.ipAddress }.toSet()).apply()
    }

    private fun loadDevices() {
        val prefs = getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
        val ipSet = prefs.getStringSet("DEVICE_IPS", emptySet()) ?: emptySet()
        devices.clear()
        ipSet.forEach { devices.add(Device(it)) }
        deviceAdapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}

class DeviceAdapter(
    private val devices: List<Device>,
    private val onItemClick: (Device) -> Unit,
    private val onItemLongClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {
    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.deviceName)
        val deviceStatus: TextView = view.findViewById(R.id.deviceStatus)
        val quickStats: TextView = view.findViewById(R.id.quickStats)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        return DeviceViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false))
    }
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceName.text = device.pcName
        holder.deviceStatus.text = device.status
        holder.quickStats.text = device.quickStats
        holder.itemView.setOnClickListener { onItemClick(device) }
        holder.itemView.setOnLongClickListener { onItemLongClick(device); true }
    }
    override fun getItemCount() = devices.size
}