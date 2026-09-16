package com.musicplayer.app.ui.screens.file

import com.musicplayer.app.ui.theme.LocalAppColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.musicplayer.app.ui.theme.parseBackgroundColor
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicplayer.app.data.settings.CROSSFADE_MAX_MS
import com.musicplayer.app.data.settings.CROSSFADE_MIN_MS
import com.musicplayer.app.data.settings.CROSSFADE_STEP_MS
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.musicplayer.app.ui.components.ColorPicker
import com.musicplayer.app.ui.components.CustomSlider
import com.musicplayer.app.ui.components.GlassIconButton
import com.musicplayer.app.ui.components.toHexString
import com.musicplayer.app.ui.components.GlassTextField
import com.musicplayer.app.ui.components.ToggleRow
import com.musicplayer.app.ui.components.glassSurface
import com.musicplayer.app.ui.theme.interTextStyle
import com.musicplayer.app.util.AuthGateManager
import com.musicplayer.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/** Slider stops between min and max, exclusive of both ends, as CustomSlider counts them. */
private val CROSSFADE_STEPS = ((CROSSFADE_MAX_MS - CROSSFADE_MIN_MS) / CROSSFADE_STEP_MS) - 1

/**
 * Toggling a setting that itself requires authorization needs auth first, unless the 5-minute
 * settings-auth override window (started by the previous successful unlock) is still active.
 */
private suspend fun authorizeSettingsChange(authGateManager: AuthGateManager): Boolean {
    if (authGateManager.isSettingsAuthOverrideActive()) return true
    val ok = authGateManager.authenticate("Authenticate to change this setting")
    if (ok) authGateManager.startSettingsAuthOverride()
    return ok
}

@Composable
fun SettingsScreen(viewModel: MainViewModel, authGateManager: AuthGateManager, modifier: Modifier = Modifier) {
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    val notificationsBlocked by viewModel.notificationsBlocked.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Locally-edited copy of the hex field. While it has focus the local text is the truth and the
    // stored value is ignored entirely; the store only wins back once editing stops.
    //
    // The store lags a keystroke or two behind - writes go through DataStore and come back
    // asynchronously - so any attempt to reconcile the two *while* typing feeds a stale string
    // into the field, which rewrites the text under the caret and lands the next character in the
    // wrong place. Anything that changes the colour from elsewhere (the picker, a preset) sets
    // hexInput directly, so nothing is missed by not reconciling here.
    var hexInput by remember { mutableStateOf(settings.backgroundColorHex) }
    var hexFieldFocused by remember { mutableStateOf(false) }
    if (!hexFieldFocused && settings.backgroundColorHex != hexInput) {
        hexInput = settings.backgroundColorHex
    }

    var seedInput by remember { mutableStateOf(settings.shuffleSeed) }
    var seedFieldFocused by remember { mutableStateOf(false) }
    if (!seedFieldFocused && settings.shuffleSeed != seedInput) {
        seedInput = settings.shuffleSeed
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(24.dp))
        Text(text = "Settings", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(34.dp))

        ToggleRow(
            label = "Notification Media Control",
            checked = settings.notificationMediaControl,
            onToggle = { viewModel.setNotificationMediaControl(it) },
            bold = false,
        )
        // A denied notification permission can't be re-requested from inside the app - Android
        // only shows that dialog once - so without this the controls stay invisible with no
        // explanation and no way back.
        if (notificationsBlocked && settings.notificationMediaControl) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Notifications are blocked for this app, so media controls can't be shown. Tap to open system settings.",
                style = interTextStyle(14, bold = false),
                color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { viewModel.openNotificationSettings() },
            )
        }
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Background Playback",
            checked = settings.backgroundPlayback,
            onToggle = { viewModel.setBackgroundPlayback(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Require Authorization to open",
            checked = settings.requireAuthToOpen,
            onToggle = { newValue ->
                scope.launch {
                    if (authorizeSettingsChange(authGateManager)) viewModel.setRequireAuthToOpen(newValue)
                }
            },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Require Authorization to delete",
            checked = settings.requireAuthToDelete,
            onToggle = { newValue ->
                scope.launch {
                    if (authorizeSettingsChange(authGateManager)) viewModel.setRequireAuthToDelete(newValue)
                }
            },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Two-Finger Brightness Gesture",
            checked = settings.twoFingerBrightnessGesture,
            onToggle = { viewModel.setTwoFingerBrightnessGesture(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Three-Finger Volume Gesture",
            checked = settings.threeFingerVolumeGesture,
            onToggle = { viewModel.setThreeFingerVolumeGesture(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Glass theming",
            checked = settings.glassTheming,
            onToggle = { viewModel.setGlassTheming(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Hide icon backgrounds",
            checked = settings.hideIconBackgrounds,
            onToggle = { viewModel.setHideIconBackgrounds(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "24-Hour Clock",
            checked = settings.twentyFourHourClock,
            onToggle = { viewModel.setTwentyFourHourClock(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))

        Text(text = "Crossfade", style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CustomSlider(
                value = settings.crossfadeMs.toFloat(),
                onValueChange = { viewModel.setCrossfadeMs(it.toInt()) },
                valueRange = CROSSFADE_MIN_MS.toFloat()..CROSSFADE_MAX_MS.toFloat(),
                steps = CROSSFADE_STEPS,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            // Fixed width: letting this size to its text made the slider beside it grow and
            // shrink as the number changed length while dragging.
            Text(
                text = if (settings.crossfadeMs == 0) "Off" else "${settings.crossfadeMs} ms",
                style = interTextStyle(16, bold = false),
                color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(72.dp),
            )
        }
        Spacer(Modifier.height(34.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(shape = RoundedCornerShape(10.dp))
                .padding(16.dp),
        ) {
            Text(text = "Background colour", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The field edits local state and pushes outward, rather than being driven by the
                // stored value. Settings round-trip through DataStore, so feeding the stored string
                // back in rewrote the text mid-keystroke and threw the caret to the end - which is
                // what made typing a colour produce something else entirely.
                GlassTextField(
                    value = hexInput,
                    onValueChange = { typed ->
                        hexInput = typed
                        viewModel.setBackgroundColorHex(typed)
                    },
                    onFocusChanged = { hexFieldFocused = it },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                // Live swatch of whatever's currently entered.
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(parseBackgroundColor(hexInput))
                        .border(1.dp, LocalAppColors.current.onBackground.copy(alpha = 0.3f), CircleShape),
                )
            }
            Spacer(Modifier.height(16.dp))
            ColorPicker(
                color = parseBackgroundColor(settings.backgroundColorHex),
                onColorChange = { picked ->
                    val hex = picked.toHexString()
                    hexInput = hex
                    viewModel.setBackgroundColorHex(hex)
                },
            )
            Spacer(Modifier.height(16.dp))
            ToggleRow(
                label = "New colour each day",
                checked = settings.dailyRandomBackground,
                onToggle = { viewModel.setDailyRandomBackground(it) },
                bold = false,
            )
            Spacer(Modifier.height(16.dp))
            ToggleRow(
                label = "Invert light/dark",
                checked = settings.invertContrast,
                onToggle = { viewModel.setInvertContrast(it) },
                bold = false,
            )
        }
        Spacer(Modifier.height(34.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(shape = RoundedCornerShape(10.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Shuffle seed", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
                Spacer(Modifier.width(20.dp))
                GlassIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Regenerate shuffle seed",
                    onClick = { viewModel.refreshShuffleSeed() },
                    // The spec's 12px is a drawing measurement, not a usable touch target.
                    circleSize = 30.dp,
                    iconSize = 18.dp,
                    enabled = !settings.shuffleSeedLocked,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassTextField(
                    value = seedInput,
                    onValueChange = { typed ->
                        if (!settings.shuffleSeedLocked) {
                            seedInput = typed
                            viewModel.setShuffleSeedText(typed)
                        }
                    },
                    onFocusChanged = { seedFieldFocused = it },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                GlassIconButton(
                    icon = if (settings.shuffleSeedLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = "Toggle shuffle seed lock",
                    onClick = { viewModel.setShuffleSeedLocked(!settings.shuffleSeedLocked) },
                    circleSize = 30.dp,
                    iconSize = 18.dp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
