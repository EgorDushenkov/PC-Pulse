package com.example.pc

import android.content.Context

object WidgetFactory {

    fun createControlsCard(context: Context, onScreenshot: () -> Unit, onSleep: () -> Unit, onShutdown: () -> Unit): ControlsWidgetView {
        return ControlsWidgetView(context).apply {
            setCallbacks(onScreenshot, onSleep, onShutdown)
        }
    }

    fun createAudioMixerCard(context: Context, onVolumeChange: (String, Int) -> Unit): AudioMixerWidgetView {
        return AudioMixerWidgetView(context).apply {
            setCallbacks(onVolumeChange)
        }
    }

    fun createDisksCard(context: Context): StorageWidgetView {
        return StorageWidgetView(context)
    }

    fun createFansCard(context: Context): CoolingWidgetView {
        return CoolingWidgetView(context)
    }

    fun createProcsCard(context: Context, onKill: (Int) -> Unit): TopProcessesWidgetView {
        return TopProcessesWidgetView(context).apply {
            setCallbacks(onKill)
        }
    }

    fun createCpuCard(context: Context): CpuWidgetView {
        return CpuWidgetView(context)
    }

    fun createRamCard(context: Context): RamWidgetView {
        return RamWidgetView(context)
    }

    fun createGpuCard(context: Context): GpuWidgetView {
        return GpuWidgetView(context)
    }

    fun createNetworkCard(context: Context): NetworkWidgetView {
        return NetworkWidgetView(context)
    }
}
