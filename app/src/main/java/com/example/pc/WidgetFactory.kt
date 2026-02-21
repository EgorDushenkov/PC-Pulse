package com.example.pc

import android.content.Context
import android.view.LayoutInflater

object WidgetFactory {

    // Фабрика создает виджеты. Данные в них будут загружаться через UpdatableWidget.updateData()

    fun createControlsCard(context: Context, onScreenshot: () -> Unit, onSleep: () -> Unit, onShutdown: () -> Unit): ControlsWidgetView {
        return ControlsWidgetView(context) // TODO: Передать коллбэки
    }

    fun createAudioMixerCard(context: Context, inflater: LayoutInflater, mutedSessions: MutableSet<String>, onMute: (String, Boolean) -> Unit): AudioMixerWidgetView {
        return AudioMixerWidgetView(context) // TODO: Передать коллбэки
    }

    fun createDisksCard(context: Context): StorageWidgetView {
        return StorageWidgetView(context)
    }

    fun createFansCard(context: Context): CoolingWidgetView {
        return CoolingWidgetView(context)
    }

    fun createProcsCard(context: Context, onKill: (Int) -> Unit): TopProcessesWidgetView {
        return TopProcessesWidgetView(context) // TODO: Передать коллбэки
    }
}
