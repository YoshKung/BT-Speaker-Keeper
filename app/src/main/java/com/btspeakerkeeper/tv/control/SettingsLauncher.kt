package com.btspeakerkeeper.tv.control

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

object SettingsLauncher {
    private const val GOOGLE_TV_CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT"
    private const val BLUETOOTH_DEVICE_PICKER = "android.bluetooth.devicepicker.action.LAUNCH"

    fun openConnectSettings(context: Context) {
        openBluetoothSettings(context)
    }

    fun openBluetoothSettings(context: Context) {
        val appContext = context.applicationContext
        val bluetoothIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (canResolve(appContext, bluetoothIntent) && tryStartActivity(appContext, bluetoothIntent)) {
            logDebug(appContext, "Opened Bluetooth settings")
        } else {
            logDebug(appContext, "Bluetooth settings unavailable; opened fresh Settings home")
            openFreshSettingsHome(appContext)
        }
    }

    fun openFreshSettingsHome(context: Context) {
        val appContext = context.applicationContext
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        tryStartActivity(appContext, settingsIntent)
    }

    fun openPairAccessorySettings(context: Context) {
        val appContext = context.applicationContext
        val intents = listOf(
            Intent(GOOGLE_TV_CONNECT_INPUT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(BLUETOOTH_DEVICE_PICKER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

        for (intent in intents) {
            if (canResolve(appContext, intent) && tryStartActivity(appContext, intent)) {
                logDebug(appContext, "Opened pair accessory route ${intent.action}")
                return
            }
        }
        logDebug(appContext, "Pair accessory routes unavailable; opened fresh Settings home")
        openFreshSettingsHome(appContext)
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun canResolve(context: Context, intent: Intent): Boolean {
        return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (exception: RuntimeException) {
            false
        }
    }

    private fun logDebug(context: Context, message: String) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d(TAG, message)
        }
    }

    private const val TAG = "BtKeeperSettingsLauncher"
}
