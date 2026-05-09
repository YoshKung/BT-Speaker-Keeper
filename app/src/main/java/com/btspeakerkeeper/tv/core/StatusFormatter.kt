package com.btspeakerkeeper.tv.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object StatusFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun formatTime(millis: Long?, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (millis == null || millis <= 0L) {
            return "Never"
        }
        return formatter.format(Instant.ofEpochMilli(millis).atZone(zoneId))
    }
}
