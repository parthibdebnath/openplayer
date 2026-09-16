package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextOverflow
import com.musicplayer.app.ui.theme.interTextStyle
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.interTextStyle
import com.musicplayer.app.util.formatClockTime
import com.musicplayer.app.util.formatDate
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Minimum height reserved for the playlist name. Held even when there's nothing to show, so the
 * clock above it keeps the same position on every screen whether or not something is playing -
 * previously the row collapsed to nothing and everything above it shifted.
 */
private val PLAYLIST_LABEL_MIN_HEIGHT = 30.dp

/**
 * The header every screen shares: the clock, with the now-playing playlist name beneath it.
 *
 * @param overlay drawn over the playlist name - the Play screen puts its output-switch
 *   confirmation here, which the spec places on top of that text.
 */
@Composable
fun TopHeader(
    playlistLabel: String,
    twentyFourHour: Boolean,
    modifier: Modifier = Modifier,
    onOpenSleepTimer: (() -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    // The top inset lives here rather than at each call site, so the header sits at exactly the
    // same height on every screen - it used to be 12dp higher on the Play screen, which showed up
    // as the clock jumping when switching tabs.
    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            TopClock(twentyFourHour = twentyFourHour, onOpenSleepTimer = onOpenSleepTimer)
        }
        Box(
            // heightIn, not height: the overlay is taller than the label, and it needs the row to
            // grow rather than spill outside its own bounds where it would stop receiving touches.
            modifier = Modifier.fillMaxWidth().heightIn(min = PLAYLIST_LABEL_MIN_HEIGHT),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (playlistLabel.isNotBlank()) {
                Text(
                    text = playlistLabel,
                    style = interTextStyle(18, bold = true),
                    color = LocalAppColors.current.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                )
            }
            overlay?.invoke(this)
        }
    }
}

/**
 * Time + date, shown at all times near the top on every screen. On the Play screen only, a
 * double-tap or a ~2s long-press opens the sleep timer overlay (per spec).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopClock(
    twentyFourHour: Boolean,
    modifier: Modifier = Modifier,
    onOpenSleepTimer: (() -> Unit)? = null,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val formatted = formatClockTime(now, twentyFourHour)
    val dateText = formatDate(now)

    val clickModifier = if (onOpenSleepTimer != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
            onDoubleClick = onOpenSleepTimer,
            onLongClick = onOpenSleepTimer,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .then(clickModifier),
        verticalAlignment = Alignment.Bottom,
    ) {
        // alignByBaseline (not the Row's own Bottom alignment) so differently-sized text lines
        // up on its true typographic baseline instead of by bounding-box bottom, which visually
        // sat the smaller AM/PM text a bit lower than the time.
        Text(
            text = formatted.time,
            style = interTextStyle(20, bold = true),
            color = LocalAppColors.current.onBackground,
            modifier = Modifier.alignByBaseline(),
        )
        formatted.amPm?.let {
            Spacer(Modifier.width(4.dp))
            Text(
                text = it,
                style = interTextStyle(14, bold = true),
                color = LocalAppColors.current.onBackground,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = dateText,
            style = interTextStyle(14, bold = false),
            color = LocalAppColors.current.onBackground,
            modifier = Modifier.alignByBaseline(),
        )
    }
}
