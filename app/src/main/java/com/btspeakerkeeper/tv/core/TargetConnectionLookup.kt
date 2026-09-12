package com.btspeakerkeeper.tv.core

object TargetConnectionLookup {
    fun resolve(
        connectedDevices: List<KnownBluetoothDevice>,
        targetName: String,
        targetAddress: String,
        fallback: () -> SpeakerConnectionState,
    ): SpeakerConnectionState = if (targetIndex(connectedDevices, targetName, targetAddress) != null) {
        SpeakerConnectionState.CONNECTED
    } else {
        fallback()
    }

    fun targetIndex(devices: List<KnownBluetoothDevice>, targetName: String, targetAddress: String): Int? =
        devices.indices.filter { index ->
            if (TargetDeviceMatcher.normalizeAddress(targetAddress).isNotEmpty()) {
                TargetDeviceMatcher.addressesEqual(devices[index].address, targetAddress)
            } else {
                SpeakerNameMatcher.matchesExactConfiguredName(devices[index].name, targetName)
            }
        }.singleOrNull()
}
