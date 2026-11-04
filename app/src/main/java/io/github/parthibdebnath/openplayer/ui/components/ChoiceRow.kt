package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.ui.skin.LocalSkin
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import io.github.parthibdebnath.openplayer.util.LocalHaptics

/**
 * One of a list where exactly one is picked - the theme, a colour scheme. A skin draws it its own
 * way (a radio button, an accent-coloured name); openplayer's own look marks the chosen one with a
 * tick. [swatch] puts a chip of that colour before the label.
 */
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    swatch: Color? = null,
) {
    val skin = LocalSkin.current
    if (skin != null) {
        skin.ChoiceRow(label, selected, onSelect, modifier, swatch)
    } else {
        DefaultChoiceRow(label, selected, onSelect, modifier, swatch)
    }
}

/** openplayer's own choice row, also what a skin without one of its own gets (in its own type). */
@Composable
fun DefaultChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier, swatch: Color?) {
    val ink = LocalAppColors.current.onBackground
    val haptics = LocalHaptics.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (!selected) haptics?.tick()
                onSelect()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            SwatchChip(swatch, ink)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            style = interTextStyle(16, bold = selected),
            color = if (selected) ink else ink.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        }
    }
}

/** A small square of a choice's colour, edged so a colour close to the page's still shows. */
@Composable
fun SwatchChip(color: Color, edge: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .background(color, RoundedCornerShape(4.dp))
            .border(1.dp, edge.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
    )
}
