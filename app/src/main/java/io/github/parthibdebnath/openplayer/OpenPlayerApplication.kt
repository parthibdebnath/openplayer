package io.github.parthibdebnath.openplayer

import android.app.Application
import io.github.parthibdebnath.openplayer.playback.PlaybackServiceStarter
import io.github.parthibdebnath.openplayer.util.BatteryMonitor

class OpenPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BatteryMonitor.start(this)
        // From the start of the process, not the first screen: playback can begin without one -
        // a headset button, or focus coming back after a call.
        PlaybackServiceStarter.watch(this)
    }
}
