package com.example.pc

// --- Основная модель --- 
data class PCStats(
    val pc_name: String,
    val time: String,
    val uptime: Float,
    val cpu: CpuData,
    val gpu: List<GpuData>,
    val ram: RamData,
    val disks: List<DiskData>,
    val network: NetworkData,
    val fans: List<FanData>,
    val procs: List<ProcessData>,
    val volume: Int,
    val audio_sessions: List<MixerSession> // <--- НОВОЕ ПОЛЕ
)

// --- Вложенные модели --- 
data class CpuData(val name: String, val usage: Double, val freq: Double, val temp: Any)
data class GpuData(val name: String, val load: Int, val temp: Float, val mem_p: Int)
data class RamData(val usage: Double, val total: Float, val used: Float, val free: Float)
data class DiskData(val dev: String, val total: Float, val used: Float, val percent: Double)
data class NetworkData(val down_kbps: Double, val up_kbps: Double)
data class FanData(val name: String, val rpm: Int)
data class ProcessData(val pid: Int, val name: String, val cpu: Double)

// --- Модель для микшера --- 
data class MixerSession(
    val name: String,
    val volume: Int
)
