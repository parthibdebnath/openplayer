package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

/** How many of the field's own recent edits are remembered, to recognise them coming back. */
private const val RECENT_EDITS = 8

/**
 * The single text-field style used consistently everywhere text is entered in the app (shuffle
 * seed, new/rename playlist, rename song, search-within-playlist): a 40dp-tall, r=10 frosted
 * glass rounded rectangle.
 *
 * Built on the state-based text field, which brings the platform's selection handles, magnifier
 * and cut/copy/paste toolbar.
 *
 * @param maxLines above 1, long text wraps onto up to this many lines and the box grows to fit, so
 *   all of a long name is visible and the caret can be put anywhere in it. A single line can only
 *   show part of it, and the cursor handle stops at the box's edge.
 * @param autoFocus focus the field and bring up the keyboard as soon as it appears.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    maxLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    digitsOnly: Boolean = false,
    readOnly: Boolean = false,
    autoFocus: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    // The caret starts at the end of existing text - renaming usually means changing the end -
    // rather than in front of the first character.
    val state = rememberTextFieldState(initialText = value, initialSelection = TextRange(value.length))
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnImeAction by rememberUpdatedState(onImeAction)

    // What the field has reported but the caller may not have handed back yet. Values come back a
    // frame or more later, by which time more may have been typed; mistaking one of those echoes
    // for a change from elsewhere would overwrite the newer text and throw the caret to the end.
    val recentEdits = remember { ArrayDeque<String>(RECENT_EDITS) }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .drop(1)
            .collect { text ->
                if (recentEdits.size == RECENT_EDITS) recentEdits.removeFirst()
                recentEdits.addLast(text)
                currentOnValueChange(text)
            }
    }
    // A genuine change from elsewhere - a colour picked, a seed regenerated - replaces the text.
    LaunchedEffect(value) {
        if (value != state.text.toString() && value !in recentEdits) {
            recentEdits.clear()
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    val transformation = remember(maxLength, digitsOnly) { EntryRules(maxLength, digitsOnly) }

    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .glassSurface(shape = RoundedCornerShape(10.dp))
            // The text field only occupies the width of its own content, so tapping the empty
            // part of the box did nothing. This makes the whole field focus the input.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() }
            // While editing, sideways drags belong to the text. Left unclaimed - which happens
            // whenever the text fits and there's nothing to scroll - they reached the tab swipe
            // around the screen and switched tabs in the middle of typing.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (focused) {
                            event.changes.forEach { change ->
                                val delta = change.positionChange()
                                if (abs(delta.x) > abs(delta.y)) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                },
            readOnly = readOnly,
            inputTransformation = transformation,
            textStyle = interTextStyle(16, bold = false).copy(color = LocalAppColors.current.onBackground),
            cursorBrush = SolidColor(LocalAppColors.current.onBackground),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            onKeyboardAction = KeyboardActionHandler { currentOnImeAction() },
            lineLimits = if (maxLines <= 1) {
                TextFieldLineLimits.SingleLine
            } else {
                TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = maxLines)
            },
        )
    }
}

/**
 * What can be typed: never a line break - these are names and values, and a wrapping field's Enter
 * key would otherwise insert one - plus an optional length limit and digits-only rule. An edit that
 * breaks a rule is undone as a whole, so the caret stays where it was.
 */
private class EntryRules(private val maxLength: Int, private val digitsOnly: Boolean) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        for (i in text.length - 1 downTo 0) {
            if (text[i] == '\n' || text[i] == '\r') replace(i, i + 1, "")
        }
        if (length > maxLength || (digitsOnly && asCharSequence().any { !it.isDigit() })) {
            revertAllChanges()
        }
    }
}
