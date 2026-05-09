package com.btspeakerkeeper.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectedSpeakerSelectionTest {
    @Test
    fun selectsFirstNonBlankConnectedSpeakerName() {
        assertEquals(
            "Demo Speaker",
            ConnectedSpeakerSelection.selectFirstNamedDevice(
                listOf("", "  Demo   Speaker ", "Other Speaker"),
            ),
        )
    }

    @Test
    fun returnsNullWhenNoConnectedDeviceHasName() {
        assertNull(ConnectedSpeakerSelection.selectFirstNamedDevice(listOf("", "   ")))
    }

    @Test
    fun returnsDistinctNormalizedConnectedSpeakerNames() {
        assertEquals(
            listOf("Demo Speaker", "Bedroom Speaker"),
            ConnectedSpeakerSelection.selectNamedDevices(
                listOf("Demo   Speaker", "Demo Speaker", "", " Bedroom Speaker "),
            ),
        )
    }

    @Test
    fun mergesConnectedAndBondedNamesWithConnectedNamesFirst() {
        assertEquals(
            listOf("Connected Speaker", "Paired Speaker"),
            ConnectedSpeakerSelection.selectKnownDeviceNames(
                connectedDeviceNames = listOf("Connected Speaker"),
                bondedDeviceNames = listOf("Paired Speaker", "Connected   Speaker"),
            ),
        )
    }

    @Test
    fun usesBondedNamesWhenNoConnectedSpeakerIsVisible() {
        assertEquals(
            listOf("Paired Speaker"),
            ConnectedSpeakerSelection.selectKnownDeviceNames(
                connectedDeviceNames = emptyList(),
                bondedDeviceNames = listOf("  Paired   Speaker  "),
            ),
        )
    }

    @Test
    fun keepsAddressForKnownDevicesAndDedupesByAddress() {
        assertEquals(
            listOf(KnownBluetoothDevice("Demo Speaker", "AA:BB:CC:DD:EE:FF")),
            ConnectedSpeakerSelection.selectKnownDevices(
                connectedDevices = listOf(KnownBluetoothDevice(" Demo   Speaker ", "aa:bb:cc:dd:ee:ff")),
                bondedDevices = listOf(KnownBluetoothDevice("Demo Speaker Alias", "AA:BB:CC:DD:EE:FF")),
            ),
        )
    }
}
