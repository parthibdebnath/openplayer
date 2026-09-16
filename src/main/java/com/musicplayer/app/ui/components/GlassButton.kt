package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
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
) {
    Box(
        modifier = modifier
            .size(circleSize)
            .glassSurface(shape = CircleShape, opacity = opacity, hideable = true)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
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
