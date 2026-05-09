package com.btspeakerkeeper.tv

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import com.btspeakerkeeper.tv.control.ReconnectCoordinator
import com.btspeakerkeeper.tv.core.SpeakerConnectionState
import com.btspeakerkeeper.tv.core.SpeakerNameMatcher
import com.btspeakerkeeper.tv.core.TriggerSource
import com.btspeakerkeeper.tv.data.AppPrefs

class PairingAssistActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPrefs
    private lateinit var targetText: TextView
    private lateinit var reasonText: TextView
    private lateinit var retryStatusText: TextView
    private lateinit var lastStatusText: TextView
    private lateinit var retryButton: Button

    private var reasonFromIntent = ""
    private var nextRetryAtMillis = 0L

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    private val retryTick = object : Runnable {
        override fun run() {
            val status = prefs.getStatus()
            if (status.lastConnectionState == SpeakerConnectionState.CONNECTED.displayName || status.automationActive) {
                render()
                return
            }

            val remainingMillis = nextRetryAtMillis - System.currentTimeMillis()
            if (remainingMillis <= 0L) {
                startPairRepairRetry()
                return
            }

            val remainingSeconds = ((remainingMillis + 999L) / 1_000L).coerceAtLeast(1L)
            retryStatusText.text = "Waiting for speaker pairing mode. Auto retry in ${remainingSeconds}s."
            handler.postDelayed(this, COUNTDOWN_TICK_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing_assist)
        prefs = AppPrefs(this)
        reasonFromIntent = intent.getStringExtra(EXTRA_REASON).orEmpty()
        bindViews()
        bindActions()
        prefs.sharedPreferences().registerOnSharedPreferenceChangeListener(preferenceListener)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reasonFromIntent = intent.getStringExtra(EXTRA_REASON).orEmpty()
        render()
        scheduleRetry()
    }

    override fun onResume() {
        super.onResume()
        render()
        scheduleRetry()
    }

    override fun onPause() {
        handler.removeCallbacks(retryTick)
        super.onPause()
    }

    override fun onDestroy() {
        prefs.sharedPreferences().unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }

    private fun bindViews() {
        targetText = findViewById(R.id.pairing_assist_target_text)
        reasonText = findViewById(R.id.pairing_assist_reason_text)
        retryStatusText = findViewById(R.id.pairing_assist_retry_status_text)
        lastStatusText = findViewById(R.id.pairing_assist_last_status_text)
        retryButton = findViewById(R.id.pairing_assist_retry_button)
    }

    private fun bindActions() {
        retryButton.setOnClickListener {
            startPairRepairRetry()
        }

        findViewById<Button>(R.id.pairing_assist_main_button).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
    }

    private fun render() {
        val settings = prefs.getSettings()
        val status = prefs.getStatus()
        val targetName = SpeakerNameMatcher.normalizeName(settings.targetDeviceName)
            .ifBlank { "target speaker" }
        val addressSuffix = settings.targetDeviceAddress
            .takeIf { it.isNotBlank() }
            ?.let { " ($it)" }
            .orEmpty()

        targetText.text = "$targetName$addressSuffix"
        reasonText.text = reasonFromIntent
            .ifBlank { status.lastError }
            .ifBlank { "Speaker is not ready for Bluetooth pairing." }
        lastStatusText.text = "State: ${status.lastConnectionState.ifBlank { "Unknown" }} | Last error: ${
            status.lastError.ifBlank { "None" }
        }"

        retryStatusText.text = when {
            status.lastConnectionState == SpeakerConnectionState.CONNECTED.displayName ->
                "Connected. Audio should be available now."

            status.automationActive ->
                "Repair pairing is running in Google TV Settings."

            else ->
                "Press and hold the speaker Bluetooth/Pair button. Auto retry starts while this screen stays open."
        }
    }

    private fun scheduleRetry() {
        val status = prefs.getStatus()
        if (status.lastConnectionState == SpeakerConnectionState.CONNECTED.displayName || status.automationActive) {
            return
        }
        nextRetryAtMillis = System.currentTimeMillis() + AUTO_RETRY_INTERVAL_MILLIS
        handler.removeCallbacks(retryTick)
        handler.post(retryTick)
    }

    private fun startPairRepairRetry() {
        handler.removeCallbacks(retryTick)
        retryStatusText.text = "Opening Google TV pairing screen..."
        ReconnectCoordinator.requestReconnect(this, TriggerSource.REPAIR_PAIR)
    }

    companion object {
        const val EXTRA_REASON = "com.btspeakerkeeper.tv.extra.PAIRING_ASSIST_REASON"
        private const val AUTO_RETRY_INTERVAL_MILLIS = 10_000L
        private const val COUNTDOWN_TICK_MILLIS = 1_000L
    }
}
