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

// Data class для хранения информации об устройстве
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
    
    private lateinit var deviceAdapter: DeviceAdapter
    private val devices = mutableListOf<Device>()

    // Используем Handler для поочередного опроса
    private var currentDeviceIndex = 0

    private val runnable = object : Runnable {
        override fun run() {
            if (devices.isNotEmpty()) {
                fetchStatsForDevice(currentDeviceIndex)
                currentDeviceIndex = (currentDeviceIndex + 1) % devices.size
            }
            // Опрашиваем следующее устройство через 2 секунды
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Инициализация вьюшек
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        fabAdd = findViewById(R.id.fab_add)
        
        // 2. Настройка RecyclerView
        setupRecyclerView()

        // 3. Загрузка сохраненных устройств
        loadDevices()

        // 4. Логика кнопки "Плюс"
        fabAdd.setOnClickListener {
            showAddDeviceDialog()
        }

        // 5. Запускаем цикл опроса
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
            } else {
                Toast.makeText(this, "Неверный IP или устройство уже существует", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }
    
    private fun showDeleteDeviceDialog(device: Device) {
        AlertDialog.Builder(this)
            .setTitle("Удалить устройство?")
            .setMessage("Вы уверены, что хотите удалить ${device.pcName} (${device.ipAddress})?")
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
                    val stats = response.body()
                    stats?.let {
                        device.isOnline = true
                        device.pcName = it.pc_name
                        device.status = "Online | ${it.time}"
                        val gpuLoad = it.gpu.getOrNull(0)?.load ?: 0
                        device.quickStats = "CPU: ${it.cpu.usage}% | GPU: $gpuLoad%"
                    }
                } else {
                    device.isOnline = false
                    device.status = "Ошибка: ${response.code()}"
                }
                deviceAdapter.notifyItemChanged(index)
            }

            override fun onFailure(call: Call<PCStats>, t: Throwable) {
                device.isOnline = false
                device.status = "Offline"
                device.quickStats = "N/A"
                deviceAdapter.notifyItemChanged(index)
            }
        })
    }
    
    // --- Управление сохранением ---

    private fun saveDevices() {
        val prefs = getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val ipSet = devices.map { it.ipAddress }.toSet()
        editor.putStringSet("DEVICE_IPS", ipSet)
        editor.apply()
    }

    private fun loadDevices() {
        val prefs = getSharedPreferences("PC_STATS_PREFS", Context.MODE_PRIVATE)
        val ipSet = prefs.getStringSet("DEVICE_IPS", emptySet()) ?: emptySet()
        devices.clear()
        ipSet.forEach { ip ->
            devices.add(Device(ip))
        }
        deviceAdapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }
}

// --- Адаптер для RecyclerView ---

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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceName.text = device.pcName
        holder.deviceStatus.text = device.status
        holder.quickStats.text = device.quickStats
        
        holder.itemView.setOnClickListener { onItemClick(device) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(device)
            true 
        }
    }

    override fun getItemCount() = devices.size
}
