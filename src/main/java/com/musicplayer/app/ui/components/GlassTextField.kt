package com.musicplayer.app.ui.components

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.musicplayer.app.ui.theme.interTextStyle

/**
 * The single text-field style used consistently everywhere text is entered in the app (shuffle
 * seed, new/rename playlist, rename song, search-within-playlist): a 40px-tall, r=10 frosted
 * glass rounded rectangle with white text.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = modifier
            .height(40.dp)
            .glassSurface(shape = RoundedCornerShape(10.dp))
            // The text field only occupies the width of its own content, so tapping the empty
            // part of the box did nothing. This makes the whole field focus the input.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            singleLine = singleLine,
            textStyle = interTextStyle(16, bold = false).copy(color = LocalAppColors.current.onBackground),
            cursorBrush = SolidColor(LocalAppColors.current.onBackground),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onImeAction() }, onSearch = { onImeAction() }),
        )
    }
}
