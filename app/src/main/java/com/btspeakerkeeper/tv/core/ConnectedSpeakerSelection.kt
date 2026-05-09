package com.btspeakerkeeper.tv.core

object ConnectedSpeakerSelection {
    fun selectFirstNamedDevice(deviceNames: List<String>): String? {
        return selectNamedDevices(deviceNames).firstOrNull()
    }

    fun selectKnownDevices(
        connectedDevices: List<KnownBluetoothDevice>,
        bondedDevices: List<KnownBluetoothDevice>,
    ): List<KnownBluetoothDevice> {
        return selectDevices(connectedDevices + bondedDevices)
    }

    fun selectKnownDeviceNames(
        connectedDeviceNames: List<String>,
        bondedDeviceNames: List<String>,
    ): List<String> {
        return selectNamedDevices(connectedDeviceNames + bondedDeviceNames)
    }

    fun selectNamedDevices(deviceNames: List<String>): List<String> {
        return deviceNames
            .asSequence()
            .map { SpeakerNameMatcher.normalizeName(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun selectDevices(devices: List<KnownBluetoothDevice>): List<KnownBluetoothDevice> {
        val seen = mutableSetOf<String>()
        return devices
            .asSequence()
            .map { device ->
                KnownBluetoothDevice(
                    name = SpeakerNameMatcher.normalizeName(device.name),
                    address = TargetDeviceMatcher.normalizeAddress(device.address),
                )
            }
            .filter { it.name.isNotEmpty() }
            .filter { device ->
                val key = device.address.ifEmpty { "name:${device.name}" }
                seen.add(key)
            }
            .toList()
    }
}
