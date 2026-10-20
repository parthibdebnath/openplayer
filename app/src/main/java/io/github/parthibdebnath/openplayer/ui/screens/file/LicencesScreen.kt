package io.github.parthibdebnath.openplayer.ui.screens.file

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.ui.theme.LocalAppColors
import io.github.parthibdebnath.openplayer.ui.theme.interTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val APACHE_2 = "Apache License 2.0"
private const val OFL = "SIL Open Font License 1.1"

private class Component(val name: String, val copyright: String, val licence: String)

/** Third-party software bundled in the app, and the licence each is used under. */
private val COMPONENTS = listOf(
    Component("Inter typeface", "Copyright 2020 The Inter Project Authors", OFL),
    Component(
        "AndroidX: Jetpack Compose, Media3, Room, DataStore, Lifecycle, Activity, Core, DocumentFile",
        "Copyright The Android Open Source Project",
        APACHE_2,
    ),
    Component("Material Icons", "Copyright Google LLC", APACHE_2),
    Component(
        "Kotlin standard library, kotlinx.coroutines, kotlinx.serialization",
        "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
        APACHE_2,
    ),
    Component("Liquid glass rendering, adapted from AndroidLiquidGlass", "Copyright 2025 Kyant", APACHE_2),
)

/** Licence texts shipped in assets/licenses, keyed by the names above. */
private val LICENCE_FILES = linkedMapOf(
    APACHE_2 to "licenses/Apache-2.0.txt",
    OFL to "licenses/Inter-OFL.txt",
)

/** Credits for the open-source software openplayer is built with, with the full licence texts. */
@Composable
fun LicencesScreen(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(text = "Open-source licences", style = interTextStyle(20, bold = true), color = colors.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "openplayer is built with the following open-source software.",
            style = interTextStyle(14, bold = false),
            color = colors.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(20.dp))

        COMPONENTS.forEach { component ->
            Text(text = component.name, style = interTextStyle(16, bold = true), color = colors.onBackground)
            Text(
                text = "${component.copyright}\n${component.licence}",
                style = interTextStyle(14, bold = false),
                color = colors.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
        }

        LICENCE_FILES.forEach { (name, path) -> LicenceText(name, path) }
        Spacer(Modifier.height(24.dp))
    }
}

/** A licence's full text, collapsed until tapped - they're long. */
@Composable
private fun LicenceText(name: String, assetPath: String) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    var text by remember(assetPath) { mutableStateOf<String?>(null) }
    if (expanded) {
        LaunchedEffect(assetPath) {
            if (text == null) {
                text = withContext(Dispatchers.IO) {
                    runCatching { context.assets.open(assetPath).bufferedReader().use { it.readText() } }
                        .getOrDefault("")
                }
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = (if (expanded) "Hide " else "Show ") + name,
            style = interTextStyle(16, bold = true),
            color = colors.onBackground,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded },
        )
        if (expanded) {
            Text(
                text = text.orEmpty(),
                style = interTextStyle(12, bold = false),
                color = colors.onBackground.copy(alpha = 0.75f),
            )
        }
    }
}
