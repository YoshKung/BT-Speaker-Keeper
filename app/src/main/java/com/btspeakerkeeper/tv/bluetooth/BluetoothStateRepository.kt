package com.btspeakerkeeper.tv.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.btspeakerkeeper.tv.core.ConnectedSpeakerSelection
import com.btspeakerkeeper.tv.core.KnownBluetoothDevice
import com.btspeakerkeeper.tv.core.SpeakerConnectionState
import com.btspeakerkeeper.tv.core.SpeakerNameMatcher
import com.btspeakerkeeper.tv.core.TargetConnectionLookup
import com.btspeakerkeeper.tv.core.TimedResult

class BluetoothStateRepository(private val context: Context) {
    private val appContext = context.applicationContext

    fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun checkTargetState(
        targetName: String,
        targetAddress: String = "",
        callback: (BluetoothCheckResult) -> Unit,
    ) {
        if (SpeakerNameMatcher.normalizeName(targetName).isEmpty()) {
            callback(BluetoothCheckResult(SpeakerConnectionState.TARGET_NOT_CONFIGURED, message = "Target speaker name is empty"))
            return
        }

        if (!hasBluetoothConnectPermission()) {
            callback(BluetoothCheckResult(SpeakerConnectionState.MISSING_PERMISSION, message = "Nearby Devices permission missing"))
            return
        }

        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            callback(BluetoothCheckResult(SpeakerConnectionState.BLUETOOTH_UNAVAILABLE, message = "Bluetooth adapter is unavailable or off"))
            return
        }

        val handler = Handler(Looper.getMainLooper())
        lateinit var timeout: Runnable
        val completion = TimedResult(
            startedAtMillis = SystemClock.elapsedRealtime(),
            timeoutMillis = 4_000L,
            timeoutValue = BluetoothCheckResult(
                SpeakerConnectionState.PROFILE_UNAVAILABLE,
                message = "A2DP state check timed out",
            ),
        ) { result ->
            handler.removeCallbacks(timeout)
            callback(result)
        }
        timeout = Runnable { completion.expire(SystemClock.elapsedRealtime()) }
        handler.postDelayed(timeout, 4_000L)

        val connected = try { adapter.getProfileProxy(
            appContext,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    handler.post {
                        val result = try {
                            if (profile == BluetoothProfile.A2DP && proxy is BluetoothA2dp) {
                                checkWithA2dp(proxy, adapter, targetName, targetAddress)
                            } else {
                                BluetoothCheckResult(SpeakerConnectionState.PROFILE_UNAVAILABLE)
                            }
                        } finally {
                            // Even a proxy arriving after timeout must be released.
                            runCatching { adapter.closeProfileProxy(profile, proxy) }
                        }
                        completion.complete(result, SystemClock.elapsedRealtime())
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    handler.post {
                        completion.complete(
                            BluetoothCheckResult(SpeakerConnectionState.PROFILE_UNAVAILABLE),
                            SystemClock.elapsedRealtime(),
                        )
                    }
                }
            },
            BluetoothProfile.A2DP,
        ) } catch (exception: RuntimeException) {
            completion.complete(
                BluetoothCheckResult(
                    if (exception is SecurityException) SpeakerConnectionState.MISSING_PERMISSION else SpeakerConnectionState.ERROR,
                    message = "A2DP state check could not start",
                ),
                SystemClock.elapsedRealtime(),
            )
            false
        }

        if (!connected) {
            completion.complete(
                BluetoothCheckResult(SpeakerConnectionState.PROFILE_UNAVAILABLE, deviceName = targetName),
                SystemClock.elapsedRealtime(),
            )
        }
    }

    fun findConnectedA2dpSpeaker(callback: (ConnectedSpeakerLookupResult) -> Unit) {
        findConnectedA2dpSpeakers { result ->
            callback(
                when (result) {
                    is ConnectedSpeakerScanResult.Found -> ConnectedSpeakerLookupResult.Found(result.deviceNames.first())
                    ConnectedSpeakerScanResult.NoConnectedSpeaker -> ConnectedSpeakerLookupResult.NoConnectedSpeaker
                    ConnectedSpeakerScanResult.MissingPermission -> ConnectedSpeakerLookupResult.MissingPermission
                    ConnectedSpeakerScanResult.BluetoothUnavailable -> ConnectedSpeakerLookupResult.BluetoothUnavailable
                    ConnectedSpeakerScanResult.ProfileUnavailable -> ConnectedSpeakerLookupResult.ProfileUnavailable
                    is ConnectedSpeakerScanResult.Error -> ConnectedSpeakerLookupResult.Error(result.message)
                },
            )
        }
    }

    fun findConnectedA2dpSpeakers(callback: (ConnectedSpeakerScanResult) -> Unit) {
        if (!hasBluetoothConnectPermission()) {
            callback(ConnectedSpeakerScanResult.MissingPermission)
            return
        }

        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            callback(ConnectedSpeakerScanResult.BluetoothUnavailable)
            return
        }

        val connected = adapter.getProfileProxy(
            appContext,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.A2DP) {
                        callback(ConnectedSpeakerScanResult.ProfileUnavailable)
                        return
                    }

                    val result = readKnownA2dpSpeakers(adapter, proxy as BluetoothA2dp)
                    adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    callback(result)
                }

                override fun onServiceDisconnected(profile: Int) = Unit
            },
            BluetoothProfile.A2DP,
        )

        if (!connected) {
            callback(ConnectedSpeakerScanResult.ProfileUnavailable)
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        return appContext.getSystemService(BluetoothManager::class.java)?.adapter
    }

    @SuppressLint("MissingPermission")
    private fun findBondedTarget(
        adapter: BluetoothAdapter,
        targetName: String,
        targetAddress: String,
    ): BluetoothDevice? {
        val devices = adapter.bondedDevices.toList()
        val index = TargetConnectionLookup.targetIndex(devices.map(::safeKnownDevice), targetName, targetAddress)
        return index?.let(devices::get)
    }

    @SuppressLint("MissingPermission")
    private fun checkWithA2dp(
        a2dp: BluetoothA2dp,
        adapter: BluetoothAdapter,
        targetName: String,
        targetAddress: String,
    ): BluetoothCheckResult {
        return try {
            // A live A2DP connection can outlast a missing/transient bond record.
            // Check the exact configured identity before consulting bonded devices.
            val state = TargetConnectionLookup.resolve(
                a2dp.connectedDevices.map(::safeKnownDevice), targetName, targetAddress,
            ) {
                val targetDevice = findBondedTarget(adapter, targetName, targetAddress)
                    ?: return@resolve SpeakerConnectionState.TARGET_NOT_PAIRED
                when (a2dp.getConnectionState(targetDevice)) {
                    BluetoothProfile.STATE_CONNECTED -> SpeakerConnectionState.CONNECTED
                    BluetoothProfile.STATE_CONNECTING -> SpeakerConnectionState.CONNECTING
                    BluetoothProfile.STATE_DISCONNECTING -> SpeakerConnectionState.DISCONNECTING
                    else -> SpeakerConnectionState.DISCONNECTED
                }
            }
            BluetoothCheckResult(
                state = state,
                deviceName = targetName,
            )
        } catch (securityException: SecurityException) {
            BluetoothCheckResult(
                state = SpeakerConnectionState.MISSING_PERMISSION,
                deviceName = targetName,
                message = "Nearby Devices permission missing",
            )
        } catch (exception: RuntimeException) {
            BluetoothCheckResult(
                state = SpeakerConnectionState.ERROR,
                deviceName = targetName,
                message = exception.message ?: "Bluetooth state check failed",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun readConnectedA2dpSpeaker(a2dp: BluetoothA2dp): ConnectedSpeakerLookupResult {
        return when (val result = readConnectedA2dpSpeakersOnly(a2dp)) {
            is ConnectedSpeakerScanResult.Found -> ConnectedSpeakerLookupResult.Found(result.deviceNames.first())
            ConnectedSpeakerScanResult.NoConnectedSpeaker -> ConnectedSpeakerLookupResult.NoConnectedSpeaker
            ConnectedSpeakerScanResult.MissingPermission -> ConnectedSpeakerLookupResult.MissingPermission
            ConnectedSpeakerScanResult.BluetoothUnavailable -> ConnectedSpeakerLookupResult.BluetoothUnavailable
            ConnectedSpeakerScanResult.ProfileUnavailable -> ConnectedSpeakerLookupResult.ProfileUnavailable
            is ConnectedSpeakerScanResult.Error -> ConnectedSpeakerLookupResult.Error(result.message)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readKnownA2dpSpeakers(adapter: BluetoothAdapter, a2dp: BluetoothA2dp): ConnectedSpeakerScanResult {
        return try {
            val selectedDevices = ConnectedSpeakerSelection.selectKnownDevices(
                connectedDevices = a2dp.connectedDevices.map { device -> safeKnownDevice(device) },
                bondedDevices = adapter.bondedDevices.map { device -> safeKnownDevice(device) },
            )
            if (selectedDevices.isEmpty()) {
                ConnectedSpeakerScanResult.NoConnectedSpeaker
            } else {
                ConnectedSpeakerScanResult.Found(selectedDevices)
            }
        } catch (securityException: SecurityException) {
            ConnectedSpeakerScanResult.MissingPermission
        } catch (exception: RuntimeException) {
            ConnectedSpeakerScanResult.Error(exception.message ?: "Known Bluetooth device lookup failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun readConnectedA2dpSpeakersOnly(a2dp: BluetoothA2dp): ConnectedSpeakerScanResult {
        return try {
            val selectedNames = ConnectedSpeakerSelection.selectNamedDevices(
                a2dp.connectedDevices.map { device -> safeName(device) },
            )
            if (selectedNames.isEmpty()) {
                ConnectedSpeakerScanResult.NoConnectedSpeaker
            } else {
                ConnectedSpeakerScanResult.Found(selectedNames.map { name -> KnownBluetoothDevice(name) })
            }
        } catch (securityException: SecurityException) {
            ConnectedSpeakerScanResult.MissingPermission
        } catch (exception: RuntimeException) {
            ConnectedSpeakerScanResult.Error(exception.message ?: "Connected speaker lookup failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice?): String {
        return try {
            device?.name.orEmpty()
        } catch (securityException: SecurityException) {
            ""
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeAddress(device: BluetoothDevice?): String {
        return try {
            device?.address.orEmpty()
        } catch (securityException: SecurityException) {
            ""
        }
    }

    private fun safeKnownDevice(device: BluetoothDevice?): KnownBluetoothDevice {
        return KnownBluetoothDevice(
            name = safeName(device),
            address = safeAddress(device),
        )
    }
}

data class BluetoothCheckResult(
    val state: SpeakerConnectionState,
    val deviceName: String? = null,
    val message: String? = null,
)

sealed class ConnectedSpeakerLookupResult {
    data class Found(val deviceName: String) : ConnectedSpeakerLookupResult()
    data object NoConnectedSpeaker : ConnectedSpeakerLookupResult()
    data object MissingPermission : ConnectedSpeakerLookupResult()
    data object BluetoothUnavailable : ConnectedSpeakerLookupResult()
    data object ProfileUnavailable : ConnectedSpeakerLookupResult()
    data class Error(val message: String) : ConnectedSpeakerLookupResult()
}

sealed class ConnectedSpeakerScanResult {
    data class Found(val devices: List<KnownBluetoothDevice>) : ConnectedSpeakerScanResult() {
        val deviceNames: List<String>
            get() = devices.map { device -> device.name }
    }
    data object NoConnectedSpeaker : ConnectedSpeakerScanResult()
    data object MissingPermission : ConnectedSpeakerScanResult()
    data object BluetoothUnavailable : ConnectedSpeakerScanResult()
    data object ProfileUnavailable : ConnectedSpeakerScanResult()
    data class Error(val message: String) : ConnectedSpeakerScanResult()
}
