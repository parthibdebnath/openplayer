package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.interTextStyle

/**
 * The single naming/rename dialog design reused for "Name Playlist", "Rename" (playlist or song),
 * "Name Song" and "Search within playlist".
 *
 * One full-width frosted panel: bold title on its own line, then the text field and the confirm
 * checkmark side by side, both *inside* the panel with the tick at its right edge - matching the
 * sleep timer panel, which is the clearest view of this pattern in the mockups.
 */
@Composable
fun NamingDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Kept well below the text field's own opacity so the panel reads as a dark backdrop
            // with a lighter field sitting on it, as in the mockups - nested glass surfaces at
            // equal strength turned the whole thing into one flat gray slab.
            .glassSurface(shape = RoundedCornerShape(10.dp), opacity = 0.45f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(text = title, style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                onImeAction = { onConfirm(text) },
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Confirm",
                tint = LocalAppColors.current.onBackground,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onConfirm(text) },
            )
        }
    }
}
