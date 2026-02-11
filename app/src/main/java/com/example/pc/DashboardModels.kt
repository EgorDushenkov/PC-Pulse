package com.example.pc

import com.google.gson.annotations.SerializedName

// Enum для всех типов виджетов, которые могут быть в конструкторе
enum class WidgetType {
    @SerializedName("controls")
    CONTROLS,

    @SerializedName("audio_mixer")
    AUDIO_MIXER,

    @SerializedName("storage")
    STORAGE,

    @SerializedName("cooling")
    COOLING,

    @SerializedName("top_processes")
    TOP_PROCESSES,

    // Сюда можно будет добавлять новые типы в будущем
    // например, CPU, RAM, GPU, NETWORK, QUICK_LAUNCH, etc.
}

// Конфигурация отдельного виджета на дашборде
data class WidgetConfig(
    @SerializedName("type")
    val type: WidgetType,      // Тип виджета

    @SerializedName("x_position")
    val x: Int,                  // Позиция по оси X в сетке

    @SerializedName("y_position")
    val y: Int,                  // Позиция по оси Y в сетке

    @SerializedName("width_span")
    val width: Int,              // Ширина виджета в ячейках сетки

    @SerializedName("height_span")
    val height: Int              // Высота виджета в ячейках сетки
)

// Полная раскладка дашборда
data class DashboardLayout(
    @SerializedName("dashboard_name")
    val name: String,            // Имя раскладки (например, "Мой игровой дашборд")

    @SerializedName("widgets")
    val widgets: List<WidgetConfig> // Список всех виджетов на этой раскладке
)
