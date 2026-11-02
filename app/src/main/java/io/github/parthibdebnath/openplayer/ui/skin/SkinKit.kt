package io.github.parthibdebnath.openplayer.ui.skin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import io.github.parthibdebnath.openplayer.util.BatteryMonitor
import io.github.parthibdebnath.openplayer.util.LocalHaptics

/** Shared pieces the skins build from. */

/** A tap with no ripple - every skin draws its own pressed state - plus an optional hold. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.skinClick(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = if (onLongClick == null) {
    clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
} else {
    combinedClickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

/** A tap with a haptic tick, as the app's own buttons give. */
fun Modifier.skinTap(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier = composed {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    skinClick(interaction, enabled, role) {
        haptics?.click()
        onClick()
    }
}

/** Whether [interaction] is held down right now. */
@Composable
fun pressedState(interaction: MutableInteractionSource): State<Boolean> = interaction.collectIsPressedAsState()

/** The battery level, 0-100. */
@Composable
fun batteryPercent(): Int {
    val percent by BatteryMonitor.percent.collectAsState()
    return percent
}

fun Color.mix(other: Color, amount: Float): Color = lerp(this, other, amount)

/** Lowercase, the way Metro and Zune set their headings. */
fun String.metroCase(): String = lowercase()
