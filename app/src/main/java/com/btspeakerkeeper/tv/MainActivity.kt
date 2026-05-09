package com.btspeakerkeeper.tv

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.btspeakerkeeper.tv.bluetooth.ConnectedSpeakerScanResult
import com.btspeakerkeeper.tv.bluetooth.BluetoothStateRepository
import com.btspeakerkeeper.tv.control.AccessibilityUtils
import com.btspeakerkeeper.tv.control.PeriodicReconnectScheduler
import com.btspeakerkeeper.tv.control.ReconnectCoordinator
import com.btspeakerkeeper.tv.control.SettingsLauncher
import com.btspeakerkeeper.tv.core.AutoConnectDeviceToggle
import com.btspeakerkeeper.tv.core.KnownBluetoothDevice
import com.btspeakerkeeper.tv.core.SpeakerConnectionState
import com.btspeakerkeeper.tv.core.SpeakerNameMatcher
import com.btspeakerkeeper.tv.core.StatusFormatter
import com.btspeakerkeeper.tv.core.TargetDeviceMatcher
import com.btspeakerkeeper.tv.core.ToggleButtonText
import com.btspeakerkeeper.tv.core.TriggerSource
import com.btspeakerkeeper.tv.data.AppPrefs

class MainActivity : Activity() {
    private lateinit var prefs: AppPrefs
    private lateinit var skipPlaybackButton: Button
    private lateinit var periodicRetryButton: Button
    private lateinit var scanConnectedDevicesButton: Button
    private lateinit var selectedSpeakerText: TextView
    private lateinit var connectedDevicesContainer: LinearLayout
    private lateinit var permissionStatusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var maxRetryText: TextView
    private lateinit var cooldownText: TextView
    private lateinit var periodicIntervalText: TextView
    private lateinit var lastTriggerText: TextView
    private lateinit var lastStateText: TextView
    private lateinit var lastErrorText: TextView
    private lateinit var lastSuccessText: TextView

    private var scanAfterPermission = false
    private var scannedConnectedDevices: List<KnownBluetoothDevice> = emptyList()
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = AppPrefs(this)
        bindViews()
        bindActions()
        prefs.sharedPreferences().registerOnSharedPreferenceChangeListener(preferenceListener)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
        if (prefs.getSettings().autoConnectEnabled) {
            ReconnectCoordinator.requestReconnect(this, TriggerSource.APP_OPEN)
        }
    }

    override fun onDestroy() {
        prefs.sharedPreferences().unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            render()
            if (scanAfterPermission) {
                scanAfterPermission = false
                if (hasBluetoothPermission()) {
                    scanConnectedDevices()
                } else {
                    showScanError("Nearby Devices permission missing")
                }
            }
        }
    }

    private fun bindViews() {
        skipPlaybackButton = findViewById(R.id.skip_playback_button)
        periodicRetryButton = findViewById(R.id.periodic_retry_button)
        scanConnectedDevicesButton = findViewById(R.id.scan_connected_devices_button)
        selectedSpeakerText = findViewById(R.id.selected_speaker_text)
        connectedDevicesContainer = findViewById(R.id.connected_devices_container)
        permissionStatusText = findViewById(R.id.permission_status_text)
        accessibilityStatusText = findViewById(R.id.accessibility_status_text)
        maxRetryText = findViewById(R.id.max_retry_text)
        cooldownText = findViewById(R.id.cooldown_text)
        periodicIntervalText = findViewById(R.id.periodic_interval_text)
        lastTriggerText = findViewById(R.id.last_trigger_text)
        lastStateText = findViewById(R.id.last_state_text)
        lastErrorText = findViewById(R.id.last_error_text)
        lastSuccessText = findViewById(R.id.last_success_text)
    }

    private fun bindActions() {
        skipPlaybackButton.setOnClickListener {
            updateSettings { it.copy(skipWhilePlaybackActive = !it.skipWhilePlaybackActive) }
        }

        periodicRetryButton.setOnClickListener {
            updateSettings { it.copy(periodicRetryEnabled = !it.periodicRetryEnabled) }
            PeriodicReconnectScheduler.syncPeriodicWork(this)
        }

        findViewById<Button>(R.id.permission_button).setOnClickListener {
            requestBluetoothPermission()
        }
        findViewById<Button>(R.id.accessibility_button).setOnClickListener {
            SettingsLauncher.openAccessibilitySettings(this)
        }
        findViewById<Button>(R.id.connect_now_button).setOnClickListener {
            ReconnectCoordinator.requestReconnect(this, TriggerSource.MANUAL)
        }
        findViewById<Button>(R.id.repair_pair_now_button).setOnClickListener {
            ReconnectCoordinator.requestReconnect(this, TriggerSource.REPAIR_PAIR)
        }
        scanConnectedDevicesButton.setOnClickListener {
            scanConnectedDevices()
        }
        findViewById<Button>(R.id.max_retry_minus_button).setOnClickListener {
            updateSettings { it.copy(maxRetryCount = (it.maxRetryCount - 1).coerceAtLeast(1)) }
        }
        findViewById<Button>(R.id.max_retry_plus_button).setOnClickListener {
            updateSettings { it.copy(maxRetryCount = (it.maxRetryCount + 1).coerceAtMost(10)) }
        }
        findViewById<Button>(R.id.cooldown_minus_button).setOnClickListener {
            updateSettings { it.copy(cooldownMinutes = (it.cooldownMinutes - 5).coerceAtLeast(0)) }
        }
        findViewById<Button>(R.id.cooldown_plus_button).setOnClickListener {
            updateSettings { it.copy(cooldownMinutes = (it.cooldownMinutes + 5).coerceAtMost(240)) }
        }
        findViewById<Button>(R.id.periodic_interval_minus_button).setOnClickListener {
            updateSettings { it.copy(periodicRetryMinutes = (it.periodicRetryMinutes - 15).coerceAtLeast(15)) }
            PeriodicReconnectScheduler.syncPeriodicWork(this)
        }
        findViewById<Button>(R.id.periodic_interval_plus_button).setOnClickListener {
            updateSettings { it.copy(periodicRetryMinutes = (it.periodicRetryMinutes + 15).coerceAtMost(24 * 60)) }
            PeriodicReconnectScheduler.syncPeriodicWork(this)
        }
    }

    private fun render() {
        val settings = prefs.getSettings()
        val status = prefs.getStatus()

        selectedSpeakerText.text = selectedSpeakerLabel(settings.targetDeviceName, settings.autoConnectEnabled)
        skipPlaybackButton.text = ToggleButtonText.skipPlayback(settings.skipWhilePlaybackActive)
        periodicRetryButton.text = ToggleButtonText.periodicRetry(settings.periodicRetryEnabled)

        renderConnectedDevices()
        permissionStatusText.text = if (hasBluetoothPermission()) "Granted" else "Missing"
        accessibilityStatusText.text = if (AccessibilityUtils.isKeeperServiceEnabled(this)) "Enabled" else "Disabled"
        maxRetryText.text = settings.maxRetryCount.toString()
        cooldownText.text = "${settings.cooldownMinutes} min"
        periodicIntervalText.text = "${settings.periodicRetryMinutes} min"
        lastTriggerText.text = status.lastTrigger.ifBlank { "Never" }
        lastStateText.text = status.lastConnectionState.ifBlank { "Unknown" }
        lastErrorText.text = status.lastError.ifBlank { "None" }
        lastSuccessText.text = StatusFormatter.formatTime(status.lastSuccessfulConnectAtMillis)
        ensureButtonFocus()
    }

    private fun updateSettings(update: (com.btspeakerkeeper.tv.core.ReconnectSettings) -> com.btspeakerkeeper.tv.core.ReconnectSettings) {
        prefs.updateSettings(update(prefs.getSettings()))
        render()
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_PERMISSION)
        } else {
            render()
        }
    }

    private fun scanConnectedDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
            scanAfterPermission = true
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_PERMISSION)
            return
        }

        BluetoothStateRepository(this).findConnectedA2dpSpeakers { result ->
            runOnUiThread {
                when (result) {
                    is ConnectedSpeakerScanResult.Found -> {
                        scannedConnectedDevices = result.devices
                        prefs.recordState(SpeakerConnectionState.CONNECTED)
                        render()
                    }

                    ConnectedSpeakerScanResult.NoConnectedSpeaker -> showScanError(
                        "No connected or paired Bluetooth device found",
                    )

                    ConnectedSpeakerScanResult.MissingPermission -> showScanError(
                        "Nearby Devices permission missing",
                    )

                    ConnectedSpeakerScanResult.BluetoothUnavailable -> showScanError(
                        "Bluetooth is unavailable or off",
                    )

                    ConnectedSpeakerScanResult.ProfileUnavailable -> showScanError(
                        "A2DP profile is unavailable",
                    )

                    is ConnectedSpeakerScanResult.Error -> showScanError(result.message)
                }
            }
        }
    }

    private fun toggleDeviceAutoConnect(device: KnownBluetoothDevice) {
        val updated = AutoConnectDeviceToggle.toggle(
            settings = prefs.getSettings(),
            selectedDeviceName = device.name,
            selectedDeviceAddress = device.address,
        )
        prefs.updateSettings(updated)
        PeriodicReconnectScheduler.syncPeriodicWork(this)
        val state = if (updated.autoConnectEnabled) "enabled" else "disabled"
        Toast.makeText(this, "Auto Connect $state for ${updated.targetDeviceName}", Toast.LENGTH_LONG).show()
        render()
    }

    private fun renderConnectedDevices() {
        connectedDevicesContainer.removeAllViews()
        val settings = prefs.getSettings()

        if (scannedConnectedDevices.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "Press Scan Known Devices"
            emptyText.setTextColor(getColor(R.color.text_secondary))
            emptyText.textSize = 16f
            connectedDevicesContainer.addView(emptyText)
            return
        }

        scannedConnectedDevices.forEach { device ->
            val isSelected = SpeakerNameMatcher.matchesExactConfiguredName(settings.targetDeviceName, device.name) ||
                TargetDeviceMatcher.addressesEqual(settings.targetDeviceAddress, device.address)
            val enabledForDevice = isSelected && settings.autoConnectEnabled
            val button = Button(this)
            button.text = deviceButtonLabel(device, enabledForDevice)
            button.isAllCaps = false
            button.textSize = 18f
            button.setTextColor(getColor(R.color.text_primary))
            button.setBackgroundResource(R.drawable.focusable_button)
            button.isFocusable = true
            button.isClickable = true
            button.minHeight = resources.getDimensionPixelSize(R.dimen.device_button_height)
            button.setOnClickListener { toggleDeviceAutoConnect(device) }
            connectedDevicesContainer.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.device_button_height),
                ).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.device_button_spacing)
                },
            )
        }
    }

    private fun selectedSpeakerLabel(targetName: String, autoConnectEnabled: Boolean): String {
        val normalizedTarget = SpeakerNameMatcher.normalizeName(targetName)
        if (normalizedTarget.isEmpty()) {
            return "No Auto Connect device selected"
        }
        val state = if (autoConnectEnabled) "ON" else "OFF"
        return "$normalizedTarget - Auto Connect $state"
    }

    private fun deviceButtonLabel(device: KnownBluetoothDevice, autoConnectEnabled: Boolean): String {
        val addressSuffix = if (device.address.isNotBlank()) {
            " (${device.address})"
        } else {
            ""
        }
        return "${device.name}$addressSuffix - Auto Connect ${if (autoConnectEnabled) "ON" else "OFF"}"
    }

    private fun showScanError(message: String) {
        scannedConnectedDevices = emptyList()
        prefs.recordFailure(SpeakerConnectionState.ERROR, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        render()
    }

    private fun ensureButtonFocus() {
        val focusedView = currentFocus
        if (focusedView !is Button || !focusedView.isShown) {
            scanConnectedDevicesButton.requestFocus()
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSION = 4101
    }
}
