package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.interTextStyle
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The in-app confirmation "notification" used throughout the app (delete playlist, remove song,
 * switch output device): a frosted glass rounded rect with a bold title, a plain-text
 * explanation, and a checkmark to confirm. Dismissible (ignoring the action) by swiping left,
 * right, or up.
 */
@Composable
fun GlassNotification(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(dismissed) {
        if (dismissed) onDismiss()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = offset.value.x; translationY = offset.value.y }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + dragAmount) }
                    },
                    onDragEnd = {
                        val dx = offset.value.x
                        val dy = offset.value.y
                        val threshold = 120f
                        val shouldDismiss = abs(dx) > threshold || dy < -threshold
                        scope.launch {
                            if (shouldDismiss) {
                                dismissed = true
                            } else {
                                offset.animateTo(Offset.Zero)
                            }
                        }
                    },
                )
            }
            .glassSurface(shape = RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column {
                Text(text = title, style = interTextStyle(15, bold = true), color = LocalAppColors.current.onBackground)
                Text(text = message, style = interTextStyle(15, bold = false), color = LocalAppColors.current.onBackground)
            }
        }
        GlassIconButton(
            icon = Icons.Filled.Check,
            contentDescription = "Confirm",
            onClick = onConfirm,
            circleSize = 35.dp,
            iconSize = 20.dp,
        )
    }
}
