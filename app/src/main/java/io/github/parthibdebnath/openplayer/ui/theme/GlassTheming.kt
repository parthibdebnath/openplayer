package io.github.parthibdebnath.openplayer.ui.theme

import androidx.compose.runtime.compositionLocalOf

/** Whether "Glass theming" is on. When false, glass components fall back to flat pure-OLED-black shapes. */
val LocalGlassEnabled = compositionLocalOf { false }
