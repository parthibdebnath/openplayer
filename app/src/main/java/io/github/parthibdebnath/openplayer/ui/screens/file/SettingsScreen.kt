package io.github.parthibdebnath.openplayer.ui.screens.file

import kotlin.math.roundToInt
import io.github.parthibdebnath.openplayer.ui.components.GlassNotification
import io.github.parthibdebnath.openplayer.util.LocalHaptics
import io.github.parthibdebnath.openplayer.data.settings.HapticStrength
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
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
import io.github.parthibdebnath.openplayer.ui.theme.parseBackgroundColor
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.parthibdebnath.openplayer.data.settings.CROSSFADE_MAX_MS
import io.github.parthibdebnath.openplayer.data.settings.CROSSFADE_MIN_MS
import io.github.parthibdebnath.openplayer.data.settings.CROSSFADE_STEP_MS
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.parthibdebnath.openplayer.ui.components.ColorPicker
import io.github.parthibdebnath.openplayer.ui.components.CustomSlider
import io.github.parthibdebnath.openplayer.ui.components.GlassIconButton
import io.github.parthibdebnath.openplayer.ui.components.toHexString
import io.github.parthibdebnath.openplayer.ui.components.GlassTextField
import io.github.parthibdebnath.openplayer.ui.components.ToggleRow
import io.github.parthibdebnath.openplayer.ui.components.glassSurface
import io.github.parthibdebnath.openplayer.ui.components.GlassPillSwitcher
import io.github.parthibdebnath.openplayer.data.settings.OtherAppSound
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import io.github.parthibdebnath.openplayer.util.AuthGateManager
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.parthibdebnath.openplayer.data.settings.UiTheme
import io.github.parthibdebnath.openplayer.ui.components.ChoiceRow
import io.github.parthibdebnath.openplayer.ui.skin.SkinChoice
import io.github.parthibdebnath.openplayer.ui.skin.SkinChoices
import io.github.parthibdebnath.openplayer.ui.skin.SkinOption
import io.github.parthibdebnath.openplayer.ui.skin.skinOptions

/** Slider stops between min and max, exclusive of both ends, as CustomSlider counts them. */
/** Seconds per trip round the colour wheel, slowest first; see the background colour's "Speed". */
private val COLOR_CYCLE_SPEEDS = listOf(3600, 1800, 600, 300, 120, 60, 30, 15)

/** A cycle's length as the speed slider shows it: "1 h", "10 min", "30 s". */
private fun formatCyclePeriod(seconds: Int): String = when {
    seconds >= 3600 -> "${seconds / 3600} h"
    seconds >= 60 -> "${seconds / 60} min"
    else -> "$seconds s"
}

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
fun SettingsScreen(
    viewModel: MainViewModel,
    authGateManager: AuthGateManager,
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    val notificationsBlocked by viewModel.notificationsBlocked.collectAsStateWithLifecycle()
    val batteryRestricted by viewModel.batteryOptimized.collectAsStateWithLifecycle()
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

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportLibrary(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importLibrary(uri)
    }
    // Imported playlists whose folders this install can't read yet, picked again one at a time.

    Box(modifier = modifier.fillMaxSize()) {
    val skin = io.github.parthibdebnath.openplayer.ui.skin.LocalSkin.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = skin?.gutter ?: 24.dp).verticalScroll(io.github.parthibdebnath.openplayer.ui.skin.rememberSkinScrollState())) {
        Spacer(Modifier.height(if (skin != null) 12.dp else 24.dp))
        Text(text = "Settings", style = interTextStyle(20, bold = true), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(24.dp))

        SectionHeading("Playback")

        ToggleRow(
            label = "Keep controls in the notification when paused",
            checked = settings.notificationMediaControl,
            onToggle = { viewModel.setNotificationMediaControl(it) },
            bold = false,
        )
        // Music playing always has its notification: Android requires one to keep playback going
        // in the background. What's left to choose is whether it outlasts a pause.
        SettingNote("While music plays, its controls are always in the notification. Off: they go away when you pause. Headset buttons work either way.")
        // A denied notification permission can't be re-requested from inside the app - Android
        // only shows that dialog once - so without this the controls stay invisible with no
        // explanation and no way back.
        if (notificationsBlocked) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Notifications are switched off for this app, so the media controls are hidden. Tap to open system settings.",
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
            label = "Background playback",
            checked = settings.backgroundPlayback,
            onToggle = { viewModel.setBackgroundPlayback(it) },
            bold = false,
        )
        // Some manufacturers freeze apps under battery optimization a few minutes after the
        // screen goes off, cutting background playback short. Exempting the app is the user's
        // call, made in system settings - the app only points the way.
        if (batteryRestricted && settings.backgroundPlayback) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "If playback stops with the screen off, turn off battery optimization for openplayer. Tap to open system settings.",
                style = interTextStyle(14, bold = false),
                color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { viewModel.openBatteryOptimizationSettings() },
            )
        }
        Spacer(Modifier.height(34.dp))
        Text(text = "When other apps make sound", style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(12.dp))
        GlassPillSwitcher(
            items = OtherAppSound.entries.map { it.label },
            selectedIndex = settings.otherAppSound.ordinal,
            onSelect = { viewModel.setOtherAppSound(OtherAppSound.entries[it]) },
            height = 44.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // The exception is spelled out, so "keep playing" isn't read as a promise to talk over
            // a video the user has just started.
            text = "${settings.otherAppSound.description}. If another app starts its own music or video, openplayer pauses either way.",
            style = interTextStyle(14, bold = false),
            color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Restart song on open",
            checked = settings.restartSongOnOpen,
            onToggle = { viewModel.setRestartSongOnOpen(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Remember position in long tracks",
            checked = settings.rememberLongTracks,
            onToggle = { viewModel.setRememberLongTracks(it) },
            bold = false,
        )
        SettingNote("Tracks of 20 minutes or more pick up where you left off.")
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Keep each playlist's shuffle",
            checked = settings.preserveShuffleProgress,
            onToggle = { viewModel.setPreserveShuffleProgress(it) },
            bold = false,
        )
        SettingNote("Shuffling a playlist again picks up the same order where you left it, even after playing something else. Press shuffle twice for a new order.")
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Skip silence",
            checked = settings.skipSilence,
            onToggle = { viewModel.setSkipSilence(it) },
            bold = false,
        )
        SettingNote("Skips quiet stretches within songs, such as long intros and gaps.")
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Even out volume",
            checked = settings.normalizeVolume,
            onToggle = { viewModel.setNormalizeVolume(it) },
            bold = false,
        )
        SettingNote("Plays every song at about the same loudness. Uses ReplayGain tags where a song has them.")
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Keep screen on while playing",
            checked = settings.keepScreenOn,
            onToggle = { viewModel.setKeepScreenOn(it) },
            bold = false,
        )
        SettingNote("While the Play screen is open and music is playing, the screen doesn't turn off.")
        Spacer(Modifier.height(34.dp))
        Text(text = "Crossfade", style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(8.dp))
        // Held here while the slider moves, heard straight away, and saved once on release: saving
        // each step rewrote the settings file dozens of times a drag. Let go once the saved value
        // has caught up, so the readout doesn't flick back to the old one in between.
        var crossfadeDraft by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(settings.crossfadeMs, crossfadeDraft) {
            if (crossfadeDraft == settings.crossfadeMs) crossfadeDraft = null
        }
        val crossfadeShown = crossfadeDraft ?: settings.crossfadeMs
        Row(verticalAlignment = Alignment.CenterVertically) {
            CustomSlider(
                value = crossfadeShown.toFloat(),
                onValueChange = {
                    crossfadeDraft = it.toInt()
                    viewModel.previewCrossfadeMs(it.toInt())
                },
                onValueChangeFinished = { crossfadeDraft?.let(viewModel::setCrossfadeMs) },
                valueRange = CROSSFADE_MIN_MS.toFloat()..CROSSFADE_MAX_MS.toFloat(),
                steps = CROSSFADE_STEPS,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            // Fixed width: letting this size to its text made the slider beside it grow and
            // shrink as the number changed length while dragging.
            Text(
                text = if (crossfadeShown == 0) "Off" else "%.1f s".format(crossfadeShown / 1000f),
                style = interTextStyle(16, bold = false),
                color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(72.dp),
            )
        }
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "DJ mix",
            checked = settings.djMix,
            onToggle = { viewModel.setDjMix(it) },
            bold = false,
        )
        SettingNote(
            if (crossfadeShown == 0) {
                "Turn crossfade on to use it. Songs are then mixed like a DJ would: tempos and beats matched, " +
                    "basslines swapped, one song's intro laid over the other's outro."
            } else {
                "Songs are mixed like a DJ would: tempos and beats matched, basslines swapped, one song's " +
                    "intro laid over the other's outro, for up to the crossfade length. Songs without a steady " +
                    "beat are faded smoothly instead. Each song is analysed the first time it comes up."
            },
        )
        Spacer(Modifier.height(34.dp))

        SectionHeading("Privacy")
        ToggleRow(
            label = "Require authorization to open",
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
            label = "Require authorization to delete",
            checked = settings.requireAuthToDelete,
            onToggle = { newValue ->
                scope.launch {
                    if (authorizeSettingsChange(authGateManager)) viewModel.setRequireAuthToDelete(newValue)
                }
            },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))

        SectionHeading("Gestures")
        ToggleRow(
            label = "Two-finger brightness gesture",
            checked = settings.twoFingerBrightnessGesture,
            onToggle = { viewModel.setTwoFingerBrightnessGesture(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "Three-finger volume gesture",
            checked = settings.threeFingerVolumeGesture,
            onToggle = { viewModel.setThreeFingerVolumeGesture(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        Text(text = "Haptic feedback", style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(12.dp))
        val haptics = LocalHaptics.current
        GlassPillSwitcher(
            items = HapticStrength.entries.map { it.label },
            selectedIndex = settings.hapticStrength.ordinal,
            onSelect = { index ->
                val strength = HapticStrength.entries[index]
                viewModel.setHapticStrength(strength)
                // Felt straight away, at the new strength, rather than at the next tap.
                haptics?.strength = strength
                haptics?.click()
            },
            height = 44.dp,
        )
        Spacer(Modifier.height(34.dp))

        SectionHeading("Appearance")
        Text(text = "Theme", style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
        SettingNote("openplayer's own look, or one modelled on a classic interface.")
        Spacer(Modifier.height(12.dp))
        UiTheme.entries.forEach { theme ->
            ChoiceRow(
                label = if (theme == UiTheme.DEFAULT) theme.label else "${theme.label} \u00B7 ${theme.inspiration}",
                selected = settings.uiTheme == theme,
                onSelect = { viewModel.setUiTheme(theme) },
                modifier = Modifier.testTag("theme:${theme.name}"),
            )
        }
        // The theme's own options, as its original system offered them.
        val themeOptions = skinOptions(settings.uiTheme)
        if (themeOptions.isNotEmpty()) {
            val choices = SkinChoices.forTheme(settings.uiTheme, settings.skinChoices)
            themeOptions.forEach { option ->
                Spacer(Modifier.height(28.dp))
                SkinOptionPicker(
                    option = option,
                    picked = choices.of(option),
                    onPick = { viewModel.setSkinChoice(settings.uiTheme, option.key, it.id) },
                )
            }
        }
        Spacer(Modifier.height(34.dp))
        // Glass, icon backgrounds and the background colour are openplayer's own look; a theme
        // brings its own of all three.
        val ownLook = settings.uiTheme == UiTheme.DEFAULT
        if (ownLook) {
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
        }
        ToggleRow(
            label = "Scroll long titles",
            checked = settings.scrollLongTitles,
            onToggle = { viewModel.setScrollLongTitles(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        ToggleRow(
            label = "24-hour clock",
            checked = settings.twentyFourHourClock,
            onToggle = { viewModel.setTwentyFourHourClock(it) },
            bold = false,
        )
        Spacer(Modifier.height(34.dp))
        if (ownLook) {
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
                        maxLength = 9,
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
                    label = "Cycle through colours",
                    checked = settings.colorCycle,
                    onToggle = { viewModel.setColorCycle(it) },
                    bold = false,
                )
                SettingNote(
                    "The background slowly goes round the colour wheel, keeping the brightness of the colour " +
                        "above, so text never flips between light and dark.",
                )
                if (settings.colorCycle) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Speed", style = interTextStyle(16, bold = false), color = LocalAppColors.current.onBackground)
                    Spacer(Modifier.height(4.dp))
                    // Picked from a few set speeds, slow to fast, rather than any number of seconds:
                    // the difference between neighbouring speeds should be one you can see.
                    val speedIndex = COLOR_CYCLE_SPEEDS.indices.minBy { kotlin.math.abs(COLOR_CYCLE_SPEEDS[it] - settings.colorCycleSeconds) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CustomSlider(
                            value = speedIndex.toFloat(),
                            onValueChange = { viewModel.setColorCycleSeconds(COLOR_CYCLE_SPEEDS[it.roundToInt().coerceIn(COLOR_CYCLE_SPEEDS.indices)]) },
                            valueRange = 0f..COLOR_CYCLE_SPEEDS.lastIndex.toFloat(),
                            steps = COLOR_CYCLE_SPEEDS.size - 2,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        // Fixed width, like the crossfade's, so the slider doesn't resize as it changes.
                        Text(
                            text = formatCyclePeriod(COLOR_CYCLE_SPEEDS[speedIndex]),
                            style = interTextStyle(16, bold = false),
                            color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.width(72.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(34.dp))
        }

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
                    // A locked seed can still be selected and copied, just not changed.
                    readOnly = settings.shuffleSeedLocked,
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
        Spacer(Modifier.height(34.dp))

        SectionHeading("Library")
        ToggleRow(
            label = "Keep a backup in your music folders",
            checked = settings.backupToFolders,
            onToggle = { viewModel.setBackupToFolders(it) },
            bold = false,
        )
        SettingNote(
            "A small hidden file in each playlist folder holds your playlists and settings. If openplayer is " +
                "ever reinstalled, add any one of those folders and it offers to bring everything back.",
        )
        Spacer(Modifier.height(24.dp))
        SettingLink("Export playlists and settings", "Saves your playlists, renamed songs, settings and sound effects to a file.") {
            exportLauncher.launch("openplayer-backup.json")
        }
        Spacer(Modifier.height(24.dp))
        SettingLink("Import playlists and settings", "Restores them from an exported file, on this phone or a new one.") {
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        Spacer(Modifier.height(34.dp))

        SectionHeading("About")
        Text(
            text = "openplayer ${viewModel.appVersion}".trim(),
            style = interTextStyle(16, bold = false),
            color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "No ads, no account, no tracking. openplayer has no internet access.",
            style = interTextStyle(14, bold = false),
            color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Open-source licences \u203A",
            style = interTextStyle(16, bold = false),
            color = LocalAppColors.current.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenLicences,
                ),
        )
        Spacer(Modifier.height(24.dp))
    }
    }
}

/** A line of explanation under a setting. */
@Composable
private fun SettingNote(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = interTextStyle(14, bold = false),
        color = LocalAppColors.current.onBackground.copy(alpha = 0.6f),
    )
}

/** A setting that does something when tapped, rather than switching on and off. */
@Composable
private fun SettingLink(title: String, description: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Text(text = "$title \u203A", style = interTextStyle(20, bold = false), color = LocalAppColors.current.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(text = description, style = interTextStyle(14, bold = false), color = LocalAppColors.current.onBackground.copy(alpha = 0.6f))
    }
}

/** Groups the settings list, which had grown into one long run of toggles. */
@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title.uppercase(),
        style = interTextStyle(13, bold = true),
        color = LocalAppColors.current.onBackground.copy(alpha = 0.5f),
    )
    Spacer(Modifier.height(18.dp))
}

/** One of a theme's own options: swatches for a colour, a list or a switcher for the rest. */
@Composable
private fun SkinOptionPicker(option: SkinOption, picked: SkinChoice, onPick: (SkinChoice) -> Unit) {
    val ink = LocalAppColors.current.onBackground
    Text(text = option.label, style = interTextStyle(20, bold = false), color = ink)
    option.note?.let { SettingNote(it) }
    Spacer(Modifier.height(12.dp))
    when (option.style) {
        SkinOption.Style.Segmented -> GlassPillSwitcher(
            items = option.choices.map { it.label },
            selectedIndex = option.choices.indexOf(picked),
            onSelect = { onPick(option.choices[it]) },
            height = 44.dp,
            modifier = Modifier.testTag("skinOption:${option.key}"),
        )
        SkinOption.Style.List -> Column(Modifier.testTag("skinOption:${option.key}")) {
            option.choices.forEach { choice ->
                ChoiceRow(
                    label = choice.label,
                    selected = choice === picked,
                    onSelect = { onPick(choice) },
                    swatch = choice.swatch,
                )
            }
        }
        SkinOption.Style.Swatches -> {
            Text(text = picked.label, style = interTextStyle(16, bold = false), color = ink.copy(alpha = 0.75f))
            Spacer(Modifier.height(10.dp))
            SwatchGrid(option.choices, picked, onPick, Modifier.testTag("skinOption:${option.key}"))
        }
    }
}

/** Colour squares in rows, the picked one ticked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchGrid(choices: List<SkinChoice>, picked: SkinChoice, onPick: (SkinChoice) -> Unit, modifier: Modifier) {
    val ink = LocalAppColors.current.onBackground
    val haptics = LocalHaptics.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.forEach { choice ->
            val colour = choice.swatch ?: return@forEach
            val selected = choice === picked
            Box(
                Modifier
                    .size(48.dp)
                    .background(colour)
                    .then(if (selected) Modifier.border(3.dp, ink) else Modifier)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (!selected) haptics?.tick()
                        onPick(choice)
                    }
                    .semantics { contentDescription = choice.label },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}
