package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetConnectionLookupTest {
    private val address = "02:00:00:00:00:01"

    @Test fun connectedTargetWinsWithoutReadingTheMissingBondRecord() {
        assertEquals(SpeakerConnectionState.CONNECTED, TargetConnectionLookup.resolve(
            listOf(KnownBluetoothDevice("Demo Speaker", address)), "Demo Speaker", address,
        ) { error("A connected target must not depend on bonded-device lookup") })
    }

    @Test fun aSavedAddressMatchesTheConnectedDeviceEvenIfItsNameChanged() {
        assertEquals(SpeakerConnectionState.CONNECTED, TargetConnectionLookup.resolve(
            listOf(KnownBluetoothDevice("Factory name", address.lowercase())), "Demo Speaker", address,
        ) { SpeakerConnectionState.TARGET_NOT_PAIRED })
    }

    @Test fun anotherConnectedSpeakerWithTheSameNameCannotOverrideTheSavedAddress() {
        assertEquals(SpeakerConnectionState.DISCONNECTED, TargetConnectionLookup.resolve(
            listOf(KnownBluetoothDevice("Demo Speaker", "02:00:00:00:00:02")), "Demo Speaker", address,
        ) { SpeakerConnectionState.DISCONNECTED })
    }

    @Test fun nameOnlyConfigurationRequiresOneExactMatch() {
        assertEquals(SpeakerConnectionState.CONNECTED, TargetConnectionLookup.resolve(
            listOf(KnownBluetoothDevice(" Demo   Speaker ", address)), "Demo Speaker", "",
        ) { SpeakerConnectionState.TARGET_NOT_PAIRED })
        assertEquals(SpeakerConnectionState.TARGET_NOT_PAIRED, TargetConnectionLookup.resolve(
            listOf(KnownBluetoothDevice("Demo Speaker", address), KnownBluetoothDevice("Demo Speaker", "02:00:00:00:00:02")),
            "Demo Speaker", "",
        ) { SpeakerConnectionState.TARGET_NOT_PAIRED })
    }

    @Test fun disconnectedTargetKeepsTheActualFallbackState() {
        for (state in listOf(SpeakerConnectionState.CONNECTING, SpeakerConnectionState.DISCONNECTED, SpeakerConnectionState.TARGET_NOT_PAIRED)) {
            assertEquals(state, TargetConnectionLookup.resolve(emptyList(), "Demo Speaker", address) { state })
        }
    }
}
