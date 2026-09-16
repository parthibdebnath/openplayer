package com.musicplayer.app.data.settings

import kotlinx.serialization.Serializable

enum class ReverbPreset(val mix: Int, val decaySeconds: Double, val preDelayMs: Int, val dampingPercent: Int, val diffusionPercent: Int) {
    CONCERT(mix = 28, decaySeconds = 2.7, preDelayMs = 24, dampingPercent = 45, diffusionPercent = 70),
    CAVE(mix = 48, decaySeconds = 6.0, preDelayMs = 12, dampingPercent = 20, diffusionPercent = 92),
    HALL(mix = 35, decaySeconds = 4.5, preDelayMs = 28, dampingPercent = 35, diffusionPercent = 88),
    WATER(mix = 62, decaySeconds = 7.2, preDelayMs = 5, dampingPercent = 5, diffusionPercent = 35),
}

/** Allowed stepped speed values, in order slow -> fast. */
val SPEED_STEPS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f)

@Serializable
data class SpeedEffectState(
    val enabled: Boolean = false,
    val preservePitch: Boolean = true,
    val speed: Float = 1f,
)

@Serializable
data class PitchEffectState(
    val enabled: Boolean = false,
    val semitones: Int = 0,
    val cents: Int = 0,
)

@Serializable
data class ReverbEffectState(
    val enabled: Boolean = false,
    val mixPercent: Int = 0,
    val decaySeconds: Double = 0.0,
    val preDelayMs: Int = 0,
    val dampingPercent: Int = 0,
    val diffusionPercent: Int = 0,
) {
    /** Which preset (if any) the current slider values match exactly. */
    fun matchingPreset(): ReverbPreset? = ReverbPreset.entries.find {
        it.mix == mixPercent && it.decaySeconds == decaySeconds &&
            it.preDelayMs == preDelayMs && it.dampingPercent == dampingPercent &&
            it.diffusionPercent == diffusionPercent
    }

    companion object {
        fun fromPreset(preset: ReverbPreset, enabled: Boolean) = ReverbEffectState(
            enabled = enabled,
            mixPercent = preset.mix,
            decaySeconds = preset.decaySeconds,
            preDelayMs = preset.preDelayMs,
            dampingPercent = preset.dampingPercent,
            diffusionPercent = preset.diffusionPercent,
        )
    }
}

@Serializable
data class ThreeDEffectState(
    val enabled: Boolean = false,
    val intensityPercent: Int = 0,
    val widthPercent: Int = 0,
    val distancePercent: Int = 0,
    val roomPercent: Int = 0,
    /** -100 = full left (L100) .. 0 = center .. 100 = full right (R100). */
    val centerPercent: Int = 0,
)

@Serializable
data class EffectsState(
    val globalEnabled: Boolean = false,
    val speed: SpeedEffectState = SpeedEffectState(),
    val pitch: PitchEffectState = PitchEffectState(),
    val reverb: ReverbEffectState = ReverbEffectState(),
    val threeD: ThreeDEffectState = ThreeDEffectState(),
) {
    /** Whether the "FX on" indicator should show on the main play screen. */
    val isFxIndicatorOn: Boolean
        get() = globalEnabled && (speed.enabled || pitch.enabled || reverb.enabled || threeD.enabled)
}

/** Formats the Center slider's value as spec'd: L100..L1, 0, R1..R100. */
fun formatCenterValue(centerPercent: Int): String = when {
    centerPercent < 0 -> "L${-centerPercent}%"
    centerPercent > 0 -> "R${centerPercent}%"
    else -> "0%"
}
