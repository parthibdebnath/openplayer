package com.musicplayer.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.HazeState

/** Whether "Glass theming" is on. When false, glass components fall back to flat pure-OLED-black shapes. */
val LocalGlassEnabled = compositionLocalOf { true }

/** The [HazeState] backing the current screen's background-blur source; glass components read this to know what to blur. */
val LocalHazeState = compositionLocalOf { HazeState() }
