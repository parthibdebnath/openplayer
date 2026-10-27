package io.github.parthibdebnath.openplayer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.parthibdebnath.openplayer.util.LocalHaptics
import io.github.parthibdebnath.openplayer.util.Haptics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.parthibdebnath.openplayer.ui.components.GestureOverlay
import io.github.parthibdebnath.openplayer.ui.components.GestureType
import io.github.parthibdebnath.openplayer.ui.components.GlassNotification
import io.github.parthibdebnath.openplayer.ui.components.GlassPillSwitcher
import io.github.parthibdebnath.openplayer.ui.components.GlassBackdrop
import io.github.parthibdebnath.openplayer.ui.components.GlassScreenRoot
import io.github.parthibdebnath.openplayer.ui.components.LocalGlassBackdrop
import io.github.parthibdebnath.openplayer.ui.components.tabSwipe
import io.github.parthibdebnath.openplayer.ui.screens.file.FileScreen
import io.github.parthibdebnath.openplayer.ui.screens.play.PlayScreen
import io.github.parthibdebnath.openplayer.ui.screens.sfx.SfxScreen
import io.github.parthibdebnath.openplayer.ui.theme.AppColors
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.LocalGlassEnabled
import io.github.parthibdebnath.openplayer.ui.theme.LocalHideIconBackgrounds
import io.github.parthibdebnath.openplayer.ui.theme.LocalScrollLongTitles
import io.github.parthibdebnath.openplayer.ui.theme.contentColorFor
import io.github.parthibdebnath.openplayer.ui.theme.parseBackgroundColor
import io.github.parthibdebnath.openplayer.util.AuthGateManager
import io.github.parthibdebnath.openplayer.util.BrightnessVolumeController
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel

private val TOP_LEVEL_TABS = listOf("File", "Play", "SFX")
private val TOP_LEVEL_ICONS = listOf(Icons.Rounded.LibraryMusic, Icons.Rounded.PlayCircle, Icons.Rounded.GraphicEq)

private val TAB_BAR_HEIGHT = 56.dp
private val TAB_BAR_BOTTOM_MARGIN = 12.dp
private val TAB_BAR_MAX_WIDTH = 300.dp

/** Space each screen leaves free at the bottom for the floating tab bar, with a gap above it. */
private val TAB_BAR_RESERVE = TAB_BAR_HEIGHT + TAB_BAR_BOTTOM_MARGIN + 8.dp

/**
 * Without glass theming the tab bar is the original full-width text bar: the switcher's default
 * height with 8 dp above and below. The screens end where it starts, as they did when it sat
 * under them in a column.
 */
private val PLAIN_TAB_BAR_HEIGHT = 50.dp
private val PLAIN_TAB_BAR_MARGIN = 8.dp
private val PLAIN_TAB_BAR_RESERVE = PLAIN_TAB_BAR_HEIGHT + PLAIN_TAB_BAR_MARGIN * 2

@Composable
fun AppRoot(
    authGateManager: AuthGateManager,
    brightnessController: BrightnessVolumeController,
    modifier: Modifier = Modifier,
) {
    val viewModel: MainViewModel = viewModel()
    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(1) } // Play is the default/home tab
    val saveableStateHolder = rememberSaveableStateHolder()

    // Bumped when File is tapped while already on File, telling that screen to return to its
    // playlist list (so the gear's Settings screen can be dismissed from the nav bar).
    var fileResetSignal by remember { mutableIntStateOf(0) }

    // Stops playback when the app leaves the foreground, if the user has background playback off.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                // Notification access can be granted from system settings while we're away, so
                // it's re-checked on the way back in rather than only at launch.
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The gesture overlay lives here, above the whole app rather than inside the Play screen,
    // because the Play screen only occupies the area above the nav switcher - rendered there the
    // overlay stopped short of the bottom of the display instead of covering it.
    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var gesturePercent by remember { mutableIntStateOf(0) }

    // A file opened from another app plays once the app is unlocked - which it is, if this is
    // showing - and brings the player to the front.
    val pendingOpen by viewModel.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        if (pendingOpen != null) {
            viewModel.playPendingOpen()
            selectedTab = 1
        }
    }
    val openFailed by viewModel.openFailed.collectAsStateWithLifecycle()

    val background = parseBackgroundColor(settings.backgroundColorHex)
    val appColors = AppColors(
        background = background,
        onBackground = contentColorFor(background),
    )

    // The screen recording floating glass refracts; owned here so the tab bar and the gesture
    // overlay, which sit outside the screens, can reach it.
    val backdrop = remember { GlassBackdrop() }

    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context.applicationContext) }
    haptics.strength = settings.hapticStrength

    CompositionLocalProvider(
        LocalHaptics provides haptics,
        LocalGlassBackdrop provides backdrop,
        LocalGlassEnabled provides settings.glassTheming,
        LocalHideIconBackgrounds provides settings.hideIconBackgrounds,
        LocalScrollLongTitles provides settings.scrollLongTitles,
        LocalAppColors provides appColors,
    ) {
        Box(modifier = modifier.fillMaxSize().background(appColors.background)) {
            // The screens fill the whole display and the tab bar floats over them, rather than
            // taking a strip of its own. Each screen leaves room at the bottom for the bar.
            GlassScreenRoot(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxSize()
                    .tabSwipe { direction ->
                        selectedTab = (selectedTab + direction).coerceIn(0, TOP_LEVEL_TABS.lastIndex)
                    },
            ) {
                val screenModifier = Modifier.fillMaxSize()
                    .padding(bottom = if (settings.glassTheming) TAB_BAR_RESERVE else PLAIN_TAB_BAR_RESERVE)
                // Retains each tab's saveable state - where the File tab had navigated to, its
                // scroll position - so switching back doesn't rebuild from scratch.
                saveableStateHolder.SaveableStateProvider(selectedTab) {
                    when (selectedTab) {
                        0 -> FileScreen(
                            viewModel = viewModel,
                            authGateManager = authGateManager,
                            resetSignal = fileResetSignal,
                            modifier = screenModifier,
                        )
                        1 -> PlayScreen(
                            viewModel = viewModel,
                            brightnessController = brightnessController,
                            onGestureChanged = { type, percent ->
                                gestureType = type
                                gesturePercent = percent
                            },
                            onOpenLibrary = { selectedTab = 0 },
                            modifier = screenModifier,
                        )
                        2 -> SfxScreen(viewModel = viewModel, modifier = screenModifier)
                    }
                }
            }
            GlassPillSwitcher(
                items = TOP_LEVEL_TABS,
                // Icons only with glass theming; the plain theme keeps its text labels.
                icons = if (settings.glassTheming) TOP_LEVEL_ICONS else null,
                floating = true,
                selectedIndex = selectedTab,
                onSelect = { index ->
                    if (index == 0 && selectedTab == 0) fileResetSignal++
                    selectedTab = index
                },
                height = if (settings.glassTheming) TAB_BAR_HEIGHT else PLAIN_TAB_BAR_HEIGHT,
                modifier = if (settings.glassTheming) {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = TAB_BAR_BOTTOM_MARGIN)
                        // A compact capsule, centred: three icons don't need the whole width.
                        .widthIn(max = TAB_BAR_MAX_WIDTH)
                        .padding(horizontal = 18.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = PLAIN_TAB_BAR_MARGIN)
                },
            )

            if (openFailed) {
                GlassNotification(
                    title = "Couldn't open that file",
                    message = "It can't be read, or it isn't a supported audio file.",
                    onConfirm = viewModel::dismissOpenFailed,
                    onDismiss = viewModel::dismissOpenFailed,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        // Floats over the screen; see SettingsScreen's folder prompt.
                        .background(appColors.background, RoundedCornerShape(10.dp)),
                )
            }

            GestureOverlay(
                type = gestureType,
                percent = gesturePercent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
