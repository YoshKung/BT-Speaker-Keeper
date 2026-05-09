package com.btspeakerkeeper.tv.control

import android.content.Context
import android.media.AudioManager

object PlaybackDetector {
    fun isPlaybackActive(context: Context): Boolean {
        return try {
            context.applicationContext.getSystemService(AudioManager::class.java)?.isMusicActive == true
        } catch (exception: RuntimeException) {
            false
        }
    }
}
