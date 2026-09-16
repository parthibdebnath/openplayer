package com.musicplayer.app.util

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings

/**
 * Applies the two-finger brightness / three-finger volume gestures. Brightness is set as a
 * per-window override (`WindowManager.LayoutParams.screenBrightness`) rather than the system-wide
 * setting, so no special permission is needed and other apps are unaffected.
 */
class BrightnessVolumeController(private val activity: Activity) {
    private val audioManager = activity.getSystemService(Activity.AUDIO_SERVICE) as AudioManager

    fun currentBrightnessPercent(): Int {
        val attrs = activity.window.attributes
        val current = attrs.screenBrightness
        if (current in 0f..1f) return (current * 100).toInt()
        return runCatching {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128) * 100 / 255
    }

    fun setBrightnessPercent(percent: Int) {
        val clamped = percent.coerceIn(1, 100) // never allow the window to go fully to 0 (unreadable black-out)
        val attrs = activity.window.attributes
        attrs.screenBrightness = clamped / 100f
        activity.window.attributes = attrs
    }

    fun currentVolumePercent(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return (current * 100) / max
    }

    fun setVolumePercent(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = ((percent.coerceIn(0, 100) / 100f) * max).toInt().coerceIn(0, max)
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }
}
