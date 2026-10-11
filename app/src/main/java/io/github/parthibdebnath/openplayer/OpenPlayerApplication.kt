package io.github.parthibdebnath.openplayer

import android.app.Application
import io.github.parthibdebnath.openplayer.util.BatteryMonitor

class OpenPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BatteryMonitor.start(this)
    }
}
