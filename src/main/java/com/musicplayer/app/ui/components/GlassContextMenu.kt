package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.musicplayer.app.ui.theme.interTextStyle

data class ContextMenuItem(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The hold-to-open context menu used on playlist and song rows: a frosted, rounded (r=10) box
 * listing text actions, with the destructive one (Delete/Remove) bolded.
 */
@Composable
fun GlassContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<ContextMenuItem>,
    offset: IntOffset = IntOffset.Zero,
) {
    if (!expanded) return
    Popup(
        offset = offset,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // Flat surface, not glassSurface: a Popup renders in its own window, where the backdrop
        // blur has no source composition to sample and tears the app down. This paints the same
        // frosted-gray look directly.
        // Hugs its content, per the mockups - the menu is a small box tucked under the row that
        // was held, not a full-width panel.
        Column(
            modifier = Modifier
                .widthIn(min = 120.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C1E).copy(alpha = 0.96f))
                .border(1.dp, LocalAppColors.current.onBackground.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                .padding(vertical = 10.dp, horizontal = 18.dp),
        ) {
            items.forEach { item ->
                Text(
                    text = item.label,
                    style = interTextStyle(18, bold = item.destructive),
                    color = LocalAppColors.current.onBackground,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onDismissRequest()
                            item.onClick()
                        }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
