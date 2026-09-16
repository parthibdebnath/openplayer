package com.musicplayer.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicplayer.app.ui.AppRoot
import com.musicplayer.app.ui.theme.MusicPlayerTheme
import com.musicplayer.app.util.AuthGateManager
import com.musicplayer.app.util.BrightnessVolumeController
import com.musicplayer.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goFullscreen()
        requestRuntimePermissions()
        requestIgnoreBatteryOptimizations()
        val authGateManager = AuthGateManager(this)
        val brightnessController = BrightnessVolumeController(this)

        setContent {
            MusicPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val viewModel: MainViewModel = viewModel()
                    val settings by viewModel.settingsSnapshot.collectAsStateWithLifecycle()
                    var unlocked by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    fun tryUnlock() {
                        scope.launch { unlocked = authGateManager.authenticate("Unlock Music Player") }
                    }

                    LaunchedEffect(settings.requireAuthToOpen) {
                        unlocked = if (settings.requireAuthToOpen) {
                            authGateManager.authenticate("Unlock Music Player")
                        } else {
                            true
                        }
                    }

                    if (unlocked) {
                        AppRoot(authGateManager = authGateManager, brightnessController = brightnessController)
                    } else {
                        LockedScreen(onRetry = ::tryUnlock)
                    }
                }
            }
        }
    }

    /** Status and navigation bars stay hidden until swiped for, so the app owns the whole display. */
    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The bars come back after a swipe or after returning from another app; re-hide them.
        if (hasFocus) goFullscreen()
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (permissions.isNotEmpty()) requestPermissions.launch(permissions.toTypedArray())
    }

    /**
     * Asks once to be exempted from battery optimization. Without the exemption some OEMs freeze
     * the process a few minutes after the screen goes off, which stops background playback dead.
     * Declining is fine - playback still works, it's just more likely to be cut short.
     */
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            )
        }
    }
}

@Composable
private fun LockedScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRetry),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Locked", color = Color.White)
            Text(text = "Tap to unlock", color = Color.White.copy(alpha = 0.6f))
        }
    }
}
