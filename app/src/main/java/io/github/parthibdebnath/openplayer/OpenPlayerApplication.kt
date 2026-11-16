package io.github.parthibdebnath.openplayer

import android.app.Application
import android.os.Build
import io.github.parthibdebnath.openplayer.playback.PlaybackServiceStarter
import io.github.parthibdebnath.openplayer.util.BatteryMonitor

class OpenPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The stem separation process (see StemSeparationService) only separates: no player in it.
        if (isStemProcess()) return
        BatteryMonitor.start(this)
        // From the start of the process, not the first screen: playback can begin without one -
        // a headset button, or focus coming back after a call.
        PlaybackServiceStarter.watch(this)
    }

    private fun isStemProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching { java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault("")
        }
        return name.endsWith(":stems")
    }
}
