package com.musicplayer.app

import android.app.Application
import com.musicplayer.app.util.BatteryMonitor

class MusicPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BatteryMonitor.start(this)
    }
}
