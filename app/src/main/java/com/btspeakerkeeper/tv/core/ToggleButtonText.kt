package com.btspeakerkeeper.tv.core

object ToggleButtonText {
    fun skipPlayback(enabled: Boolean): String {
        return "Skip while playback: ${onOff(enabled)}"
    }

    fun periodicRetry(enabled: Boolean): String {
        return "Periodic retry: ${onOff(enabled)}"
    }

    private fun onOff(enabled: Boolean): String {
        return if (enabled) "ON" else "OFF"
    }
}
