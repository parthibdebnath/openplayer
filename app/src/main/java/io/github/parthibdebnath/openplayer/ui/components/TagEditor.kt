package io.github.parthibdebnath.openplayer.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.data.model.MAX_TAG_LENGTH
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.data.model.Tag
import io.github.parthibdebnath.openplayer.data.model.normalizeTagName
import io.github.parthibdebnath.openplayer.data.repository.isExternal
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import io.github.parthibdebnath.openplayer.util.LocalHaptics

/**
 * Edits one song's tags: the ones it has, each with a cross to take it off; a field for a new one;
 * and the library's other tags to add with a tap, narrowed down by whatever is typed. Every change
 * is saved as it's made, so there's nothing to confirm - the tick only closes the panel.
 *
 * @param songTags the song's tags, or null while they're still being read (nothing is shown for
 * them then, rather than "No tags yet" for a moment).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(
    song: Song,
    songTags: List<Tag>?,
    allTags: List<Tag>,
    onAdd: (String) -> Unit,
    onRemove: (Tag) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val haptics = LocalHaptics.current
    var input by remember(song.id) { mutableStateOf("") }

    fun add(name: String) {
        if (normalizeTagName(name).isEmpty()) return
        haptics?.click()
        onAdd(name)
        input = ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // An opaque backing first: the panel floats over the screen, and glass alone let the
            // list underneath show through the text.
            .background(colors.background, RoundedCornerShape(10.dp))
            .glassSurface(shape = RoundedCornerShape(10.dp), opacity = 0.45f, role = GlassRole.Floating)
            // Taps on the panel stay on the panel rather than reaching the dimmed screen behind,
            // which closes it.
            .pointerInput(Unit) { detectTapGestures() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Tags", style = interTextStyle(20, bold = true), color = colors.onBackground)
                Text(
                    text = song.displayName,
                    style = interTextStyle(14, bold = false),
                    color = colors.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Done",
                tint = colors.onBackground,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }

        if (song.isExternal) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "This file was opened from another app and isn't in your library, so it can't be tagged.",
                style = interTextStyle(16, bold = false),
                color = colors.onBackground.copy(alpha = 0.6f),
            )
            return@Column
        }

        Spacer(Modifier.height(12.dp))
        if (songTags != null) {
            if (songTags.isEmpty()) {
                Text(
                    text = "No tags yet. Each tag you add becomes a list of its own in Playlists.",
                    style = interTextStyle(14, bold = false),
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    songTags.forEach { tag ->
                        TagChip(
                            name = tag.name,
                            icon = Icons.Filled.Close,
                            actionLabel = "Remove ${tag.name}",
                            onClick = {
                                haptics?.click()
                                onRemove(tag)
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = input,
                onValueChange = { input = it },
                onImeAction = { add(input) },
                maxLength = MAX_TAG_LENGTH,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add tag",
                tint = colors.onBackground.copy(alpha = if (normalizeTagName(input).isEmpty()) 0.35f else 1f),
                modifier = Modifier
                    .size(28.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { add(input) },
            )
        }

        // The library's other tags, so a tag already in use is one tap rather than retyped (and
        // possibly misspelled into a second list). Narrowed down as a name is typed.
        val have = songTags.orEmpty().mapTo(HashSet()) { it.id }
        val query = normalizeTagName(input)
        val suggestions = allTags.filter { it.id !in have && (query.isEmpty() || it.name.contains(query, ignoreCase = true)) }
        if (songTags != null && suggestions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    // A library with many tags scrolls here instead of pushing the panel off screen.
                    .heightIn(max = 168.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                suggestions.forEach { tag ->
                    TagChip(
                        name = tag.name,
                        icon = Icons.Filled.Add,
                        actionLabel = "Add ${tag.name}",
                        dimmed = true,
                        onClick = { add(tag.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagChip(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    val colors = LocalAppColors.current
    val alpha = if (dimmed) 0.6f else 1f
    Row(
        modifier = Modifier
            .glassSurface(shape = RoundedCornerShape(50), opacity = if (dimmed) 0.5f else 1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = actionLabel,
                onClick = onClick,
            )
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = interTextStyle(15, bold = false),
            color = colors.onBackground.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onBackground.copy(alpha = alpha),
            modifier = Modifier.size(16.dp),
        )
    }
}
