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
 * directly. Where the hardware supports it (Android 11+), it plays the same crisp "click" and
 * "tick" primitives the system uses, scaled; elsewhere a short pulse, whose length and - where
 * the motor allows - amplitude follow the strength. Marked as touch feedback, so turning off
 * touch vibration in system settings silences it as well.
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
        val scale = when (strength) {
            HapticStrength.OFF -> return
            HapticStrength.LIGHT -> 0.35f
            HapticStrength.MEDIUM -> 0.65f
            HapticStrength.STRONG -> 1f
        }
        val effect = if (primitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            VibrationEffect.startComposition()
                .addPrimitive(if (tick) VibrationEffect.Composition.PRIMITIVE_TICK else VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                .compose()
        } else {
            val duration = ((if (tick) 6 else 10) + scale * (if (tick) 8 else 14)).toLong()
            val amplitude = if (v.hasAmplitudeControl()) (60 + scale * 195).toInt() else VibrationEffect.DEFAULT_AMPLITUDE
            VibrationEffect.createOneShot(duration, amplitude)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                v.vibrate(effect)
            }
        }
    }
}

/** The app's haptics; null in previews and anywhere outside the app's root. */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }
