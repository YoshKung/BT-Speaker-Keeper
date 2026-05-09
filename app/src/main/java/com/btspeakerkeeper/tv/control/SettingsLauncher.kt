package com.btspeakerkeeper.tv.control

import android.content.Context
import android.content.Intent
import android.provider.Settings

object SettingsLauncher {
    private const val GOOGLE_TV_CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT"
    private const val BLUETOOTH_DEVICE_PICKER = "android.bluetooth.devicepicker.action.LAUNCH"

    fun openBluetoothSettings(context: Context) {
        val appContext = context.applicationContext
        val bluetoothIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            appContext.startActivity(bluetoothIntent)
        } catch (exception: RuntimeException) {
            appContext.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openPairAccessorySettings(context: Context) {
        val appContext = context.applicationContext
        val intents = listOf(
            Intent(GOOGLE_TV_CONNECT_INPUT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(BLUETOOTH_DEVICE_PICKER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        for (intent in intents) {
            if (tryStartActivity(appContext, intent)) {
                return
            }
        }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (exception: RuntimeException) {
            false
        }
    }
}
