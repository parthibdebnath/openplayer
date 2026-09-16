package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.app.ui.theme.InterFontFamily

/**
 * The liquid-glass segmented switcher used for File/Play/SFX, the 4 effect tabs, and (at a
 * smaller size) the reverb presets: an animated glass pill slides under whichever item is
 * selected (sized to hug that label's text, not the whole segment), and that item's label
 * becomes bold. Items split the full available width into equal segments, each label centered
 * within its own segment.
 */
@Composable
fun GlassPillSwitcher(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    fontSizeSp: Float = 13.33f,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .glassSurface(shape = RoundedCornerShape(height / 2), opacity = 0.5f),
    ) {
        val segmentWidth = maxWidth / items.size

        if (selectedIndex in items.indices) {
            // The highlight spans its whole segment - one full 1/N share of the switcher - rather
            // than hugging the label's own text width, which left it looking undersized and
            // inconsistent between short and long labels.
            // Inset on both sides so the highlight never runs flush into the rounded outer edge
            // at the first and last segments.
            val inset = 4.dp
            val animatedX by animateDpAsState(segmentWidth * selectedIndex + inset, tween(220), label = "pillX")
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .height(height - 8.dp)
                    .offset(x = animatedX)
                    .width(segmentWidth - inset * 2)
                    .glassSurface(shape = RoundedCornerShape((height - 8.dp) / 2), opacity = 1f),
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = LocalAppColors.current.onBackground,
                        fontFamily = InterFontFamily,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        fontSize = fontSizeSp.sp,
                    )
                }
            }
        }
    }
}
