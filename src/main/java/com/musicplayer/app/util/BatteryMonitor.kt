package com.musicplayer.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide battery level, registered once at startup.
 *
 * The indicator appears on every screen, so having it own the registration meant unregistering and
 * re-registering the receiver - plus a synchronous read of the sticky broadcast - on every single
 * tab switch. Those are binder round-trips to system_server on the main thread, during
 * composition, and they were a visible part of the delay in changing tabs.
 */
object BatteryMonitor {
    private val _percent = MutableStateFlow(100)
    val percent: StateFlow<Int> = _percent.asStateFlow()

    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let { readInto(it) }
            }
        }
        // ACTION_BATTERY_CHANGED is sticky, so registering hands back the current level right away
        // and seeds the flow without a separate read.
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { readInto(it) }
    }

    private fun readInto(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) _percent.value = (level * 100) / scale
    }
}
