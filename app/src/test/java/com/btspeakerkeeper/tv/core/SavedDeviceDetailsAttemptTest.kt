package com.btspeakerkeeper.tv.core

import org.junit.Assert.*
import org.junit.Test

class SavedDeviceDetailsAttemptTest {
    private val screen = UiBounds(0, 0, 1920, 1080)
    private val row = SavedDeviceRow(listOf("Demo Speaker", "เลิกเชื่อมต่อแล้ว"), UiBounds(250, 380, 880, 500), true, true, true, true)

    @Test fun opensFocusedDisconnectedTargetOnceWhenThePreviewHasNoConnect() {
        val attempt = SavedDeviceDetailsAttempt()
        assertEquals(0, attempt.takeRow(listOf(row), "Demo Speaker", "", screen))
        assertNull(attempt.takeRow(listOf(row), "Demo Speaker", "", screen))
    }

    @Test fun acceptsEnglishDisconnectedStatusAndConfiguredAddress() {
        assertEquals(0, SavedDeviceDetailsAttempt().takeRow(
            listOf(row.copy(labels = listOf("Demo Speaker", "Disconnected", "02:00:00:00:00:01"))),
            "Demo Speaker", "02:00:00:00:00:01", screen,
        ))
    }

    @Test fun rejectsConnectedRowsAndContainersWithOtherActionsOrDevices() {
        for (label in listOf("Connected", "เชื่อมต่อแล้ว", "Disconnect", "ยกเลิกการเชื่อมต่อ", "Rename", "เปลี่ยนชื่อ", "Forget", "ไม่จำ", "Kitchen Speaker", "รหัสการจับคู่ Wi-Fi", "02:00:00:00:00:02")) {
            assertNull(label, SavedDeviceDetailsAttempt().takeRow(listOf(row.copy(labels = row.labels + label)), "Demo Speaker", "02:00:00:00:00:01", screen))
        }
        assertNull(SavedDeviceDetailsAttempt().takeRow(listOf(row.copy(labels = listOf("Demo Speaker", "Connected"))), "Demo Speaker", "", screen))
    }

    @Test fun requiresAnExactTargetAndOneUnambiguousInteractiveLeftPaneRow() {
        for (candidate in listOf(row.copy(labels = listOf("Demo Speaker Mini", "Disconnected")), row.copy(focused = false), row.copy(visible = false), row.copy(enabled = false), row.copy(clickable = false), row.copy(bounds = UiBounds(1250, 380, 1880, 500)), row.copy(bounds = UiBounds(-10, 380, 880, 500)))) {
            assertNull(SavedDeviceDetailsAttempt().takeRow(listOf(candidate), "Demo Speaker", "", screen))
        }
        assertNull(SavedDeviceDetailsAttempt().takeRow(listOf(row, row.copy(bounds = UiBounds(250, 510, 880, 620))), "Demo Speaker", "", screen))
    }

    @Test fun rejectedCandidatesDoNotConsumeTheOneOpenAttempt() {
        val attempt = SavedDeviceDetailsAttempt()
        assertNull(attempt.takeRow(listOf(row.copy(focused = false)), "Demo Speaker", "", screen))
        assertEquals(0, attempt.takeRow(listOf(row), "Demo Speaker", "", screen))
    }
}
