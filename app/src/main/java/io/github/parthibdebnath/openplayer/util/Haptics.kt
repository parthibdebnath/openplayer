package io.github.parthibdebnath.openplayer.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.parthibdebnath.openplayer.data.settings.HapticStrength

/**
 * Taps back when a control is used, at the strength picked in Settings.
 *
 * The system's own `performHapticFeedback` has one fixed strength, so this drives the vibrator
 * directly. Light plays the system's crisp "click"/"tick" primitive at full scale where the
 * hardware has it (Android 11+). That primitive is as strong as primitives get, and it still felt
 * too faint, so Medium and up are plain pulses at the motor's full amplitude, getting longer -
 * on these motors, a longer pulse at full drive is the only way to feel stronger. Max is the
 * longest tap that still reads as a tap.
 *
 * Played as media vibration, not touch feedback: touch feedback is scaled down by the phone's
 * own touch-vibration intensity (low by default on many phones), which flattened every level
 * here into roughly the same faint buzz. "Off" in the app's settings switches it off.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    var strength: HapticStrength = HapticStrength.LIGHT

    private val primitives: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator?.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            ) == true
    }

    /** A press: buttons, switches, opening a menu. */
    fun click() = play(tick = false)

    /** A lighter detent: a selection moving from one item to the next. */
    fun tick() = play(tick = true)

    private fun play(tick: Boolean) {
        val v = vibrator ?: return
        if (strength == HapticStrength.OFF || !v.hasVibrator()) return
        // Pulse lengths in ms (press, detent) for the levels that use plain pulses.
        val pulseMs = when (strength) {
            HapticStrength.OFF -> return
            HapticStrength.LIGHT -> null
            HapticStrength.MEDIUM -> if (tick) 14L else 24L
            HapticStrength.STRONG -> if (tick) 26L else 45L
            HapticStrength.MAX -> if (tick) 90L else 180L
        }
        val effect = if (pulseMs == null && primitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            VibrationEffect.startComposition()
                .addPrimitive(if (tick) VibrationEffect.Composition.PRIMITIVE_TICK else VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                .compose()
        } else {
            // Light without primitives falls back to a short pulse, a little shorter than Medium.
            val duration = pulseMs ?: if (tick) 10L else 16L
            val amplitude = if (v.hasAmplitudeControl()) 255 else VibrationEffect.DEFAULT_AMPLITUDE
            VibrationEffect.createOneShot(duration, amplitude)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
            }
        }
    }
}

/** The app's haptics; null in previews and anywhere outside the app's root. */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }
