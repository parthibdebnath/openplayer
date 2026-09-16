package com.musicplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicplayer.app.ui.components.GestureOverlay
import com.musicplayer.app.ui.components.GestureType
import com.musicplayer.app.ui.components.GlassPillSwitcher
import com.musicplayer.app.ui.components.tabSwipe
import com.musicplayer.app.ui.screens.file.FileScreen
import com.musicplayer.app.ui.screens.play.PlayScreen
import com.musicplayer.app.ui.screens.sfx.SfxScreen
import com.musicplayer.app.ui.theme.AppColors
import com.musicplayer.app.ui.theme.LocalAppColors
import com.musicplayer.app.ui.theme.LocalGlassEnabled
import com.musicplayer.app.ui.theme.LocalHideIconBackgrounds
import com.musicplayer.app.ui.theme.contentColorFor
import com.musicplayer.app.ui.theme.parseBackgroundColor
import com.musicplayer.app.util.AuthGateManager
import com.musicplayer.app.util.BrightnessVolumeController
import com.musicplayer.app.viewmodel.MainViewModel

private val TOP_LEVEL_TABS = listOf("File", "Play", "SFX")

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

    val background = parseBackgroundColor(settings.backgroundColorHex)
    val appColors = AppColors(
        background = background,
        onBackground = contentColorFor(background, inverted = settings.invertContrast),
    )

    CompositionLocalProvider(
        LocalGlassEnabled provides settings.glassTheming,
        LocalHideIconBackgrounds provides settings.hideIconBackgrounds,
        LocalAppColors provides appColors,
    ) {
        Box(modifier = modifier.fillMaxSize().background(appColors.background)) {
            Column(modifier = Modifier.fillMaxSize()) {
                com.musicplayer.app.ui.components.GlassScreenRoot(
                    modifier = Modifier
                        .weight(1f)
                        .tabSwipe { direction ->
                            selectedTab = (selectedTab + direction).coerceIn(0, TOP_LEVEL_TABS.lastIndex)
                        },
                ) {
                    // Retains each tab's saveable state - where the File tab had navigated to, its
                    // scroll position - so switching back doesn't rebuild from scratch.
                    saveableStateHolder.SaveableStateProvider(selectedTab) {
                    when (selectedTab) {
                        0 -> FileScreen(
                            viewModel = viewModel,
                            authGateManager = authGateManager,
                            resetSignal = fileResetSignal,
                            modifier = Modifier.fillMaxSize(),
                        )
                        1 -> PlayScreen(
                            viewModel = viewModel,
                            brightnessController = brightnessController,
                            onGestureChanged = { type, percent ->
                                gestureType = type
                                gesturePercent = percent
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        2 -> SfxScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                    }
                    }
                }
                GlassPillSwitcher(
                    items = TOP_LEVEL_TABS,
                    selectedIndex = selectedTab,
                    onSelect = { index ->
                        if (index == 0 && selectedTab == 0) fileResetSignal++
                        selectedTab = index
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
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
