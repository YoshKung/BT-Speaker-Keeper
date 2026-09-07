package com.btspeakerkeeper.tv.core

class TimedResult<T>(
    private val startedAtMillis: Long,
    private val timeoutMillis: Long,
    private val timeoutValue: T,
    private val callback: (T) -> Unit,
) {
    private var resolved = false

    fun complete(value: T, nowMillis: Long): Boolean {
        val delivered = synchronized(this) {
            if (resolved) return false
            resolved = true
            if (nowMillis - startedAtMillis >= timeoutMillis) timeoutValue else value
        }
        callback(delivered)
        return true
    }

    fun expire(nowMillis: Long): Boolean {
        if (nowMillis - startedAtMillis < timeoutMillis) return false
        return complete(timeoutValue, nowMillis)
    }
}
