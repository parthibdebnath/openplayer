package io.github.parthibdebnath.openplayer.ui.components

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/**
 * Keeps typing in the text field the user is looking at.
 *
 * Panels that float over a screen (the tag editor, naming dialogs, the sleep timer) each open a
 * [TextEntryLayer]. A layer that appears while a field underneath it has focus takes that focus
 * into its own first field, so typing simply carries on in the new box. Before this, holding a
 * search result to edit its tags left the search box focused behind the panel, so whatever was
 * typed for a tag went into the search.
 *
 * When the layer goes away with the keyboard still up, focus goes back to the field it was taken
 * from, so closing the tag editor puts you back in the search box mid-search.
 */
class TextEntryFocus {
    internal class Field(val layer: Layer?, val requester: FocusRequester) {
        var focused = false
    }

    internal class Layer(val order: Int) {
        /** The field underneath that had focus when this layer opened, to give it back on close. */
        var takenFrom: Field? = null

        /** Whether this layer has looked for focus to take yet: only its first field does. */
        var claimed = false
    }

    private val fields = mutableListOf<Field>()
    private var layers = 0

    internal fun newLayer() = Layer(++layers)

    internal fun add(field: Field) {
        fields += field
    }

    internal fun remove(field: Field) {
        fields -= field
    }

    internal fun isRegistered(field: Field) = field in fields

    /** The focused field in any layer below [layer] (fields outside every layer are the bottom). */
    internal fun focusedBelow(layer: Layer): Field? =
        fields.lastOrNull { it.focused && (it.layer?.order ?: 0) < layer.order }
}

val LocalTextEntryFocus = staticCompositionLocalOf<TextEntryFocus?> { null }
internal val LocalTextEntryLayer = staticCompositionLocalOf<TextEntryFocus.Layer?> { null }

/** A floating panel's text fields, which take focus from fields beneath it; see [TextEntryFocus]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextEntryLayer(content: @Composable () -> Unit) {
    val focus = LocalTextEntryFocus.current
    val layer = remember(focus) { focus?.newLayer() }
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    DisposableEffect(layer) {
        onDispose {
            val previous = layer?.takenFrom ?: return@onDispose
            // Only while the keyboard is still up: put away with Back first, the user had
            // finished typing, and bringing it back for the field underneath would be a surprise.
            if (!imeVisible || focus?.isRegistered(previous) != true) return@onDispose
            // Posted: focus can't move while the panel's own nodes are still being taken down.
            Handler(Looper.getMainLooper()).post {
                if (focus.isRegistered(previous)) runCatching { previous.requester.requestFocus() }
            }
        }
    }
    CompositionLocalProvider(LocalTextEntryLayer provides layer, content = content)
}
