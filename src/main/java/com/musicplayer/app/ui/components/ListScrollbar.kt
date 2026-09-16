package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A slim scroll position indicator drawn alongside a [androidx.compose.foundation.lazy.LazyColumn],
 * for lists long enough that the scrollbar is the only cue to how far through them you are.
 *
 * Purely indicative - it isn't draggable. State is read inside the draw lambda so scrolling
 * repaints it without recomposing anything.
 */
@Composable
fun ListScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    alpha: Float = 0.35f,
) {
    // Read outside the draw lambda: a DrawScope isn't a composable, so it can't read the local.
    val contentColor = LocalAppColors.current.onBackground
    Canvas(modifier = modifier.width(3.dp).fillMaxHeight()) {
        val layoutInfo = state.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo.size
        if (totalItems == 0 || visibleItems == 0 || visibleItems >= totalItems) return@Canvas

        val visibleFraction = visibleItems.toFloat() / totalItems
        val thumbHeight = (size.height * visibleFraction).coerceAtLeast(24.dp.toPx())
        val scrollFraction =
            (state.firstVisibleItemIndex.toFloat() / (totalItems - visibleItems)).coerceIn(0f, 1f)
        drawRoundRect(
            color = contentColor.copy(alpha = alpha),
            topLeft = Offset(0f, (size.height - thumbHeight) * scrollFraction),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(size.width / 2f),
        )
    }
}
