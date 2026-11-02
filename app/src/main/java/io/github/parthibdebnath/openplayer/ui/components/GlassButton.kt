package io.github.parthibdebnath.openplayer.ui.components

import io.github.parthibdebnath.openplayer.util.LocalHaptics
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A circular frosted-glass "button": icon centered above a round hit-target circle, per the
 * spec's general button definition. [circleSize]/[iconSize] follow the relative sizes called
 * out for each specific button in the design guidelines (e.g. play 75x75/50x50).
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circleSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    opacity: Float = 1f,
    tint: Color = LocalAppColors.current.onBackground,
    enabled: Boolean = true,
    /** What the button is for; a skin can draw some kinds - Play above all - its own way. */
    kind: io.github.parthibdebnath.openplayer.ui.skin.ButtonKind = io.github.parthibdebnath.openplayer.ui.skin.ButtonKind.Plain,
) {
    val haptics = LocalHaptics.current
    val skin = io.github.parthibdebnath.openplayer.ui.skin.LocalSkin.current
    if (skin != null) {
        skin.IconButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = {
                haptics?.click()
                onClick()
            },
            modifier = modifier,
            size = circleSize,
            iconSize = iconSize,
            opacity = opacity,
            enabled = enabled,
            kind = kind,
        )
        return
    }
    Box(
        modifier = modifier
            .size(circleSize)
            .glassSurface(shape = CircleShape, opacity = opacity, hideable = true, role = GlassRole.Control)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics?.click()
                    onClick()
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The bare tick (or plus) that confirms a panel: just the glyph in openplayer's own look, and the
 * skin's confirm button under a skin - an OK button, a gel pill - where a loose glyph would look lost.
 */
@Composable
fun ConfirmGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val skin = io.github.parthibdebnath.openplayer.ui.skin.LocalSkin.current
    if (skin != null) {
        GlassIconButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            circleSize = 32.dp,
            iconSize = 20.dp,
            opacity = alpha,
            kind = io.github.parthibdebnath.openplayer.ui.skin.ButtonKind.Confirm,
        )
        return
    }
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = LocalAppColors.current.onBackground.copy(alpha = alpha),
        modifier = modifier
            .size(28.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    )
}
