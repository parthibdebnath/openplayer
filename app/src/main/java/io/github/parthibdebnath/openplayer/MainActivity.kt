package io.github.parthibdebnath.openplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import io.github.parthibdebnath.openplayer.ui.AppRoot
import io.github.parthibdebnath.openplayer.ui.theme.OpenPlayerTheme
import io.github.parthibdebnath.openplayer.util.AuthGateManager
import io.github.parthibdebnath.openplayer.util.BrightnessVolumeController
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goFullscreen()
        requestRuntimePermissions()
        val authGateManager = AuthGateManager(this)
        val brightnessController = BrightnessVolumeController(this)

        setContent {
            OpenPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val viewModel: MainViewModel = viewModel()
                    val requireAuth by viewModel.requireAuthToOpen.collectAsStateWithLifecycle()
                    // Deliberately not saved across recreation: restoring "unlocked" after the
                    // process was killed in the background would skip the lock entirely.
                    var unlocked by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    fun tryUnlock() {
                        scope.launch { unlocked = authGateManager.authenticate(UNLOCK_PROMPT) }
                    }

                    LaunchedEffect(requireAuth) {
                        when (requireAuth) {
                            null -> Unit // not read yet - stay on the blank screen below
                            false -> unlocked = true
                            // Turning the lock on from settings doesn't lock the session in progress.
                            true -> if (!unlocked) unlocked = authGateManager.authenticate(UNLOCK_PROMPT)
                        }
                    }

                    when {
                        // Nothing is shown until the setting is known; showing the app first and
                        // locking it a moment later briefly exposed it to anyone holding the phone.
                        requireAuth == null -> Box(Modifier.fillMaxSize().background(Color.Black))
                        unlocked -> AppRoot(authGateManager = authGateManager, brightnessController = brightnessController)
                        else -> LockedScreen(onRetry = ::tryUnlock)
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

    /**
     * Only notifications are asked for, so the media controls can be shown. Output devices are
     * read through AudioManager, which needs no Bluetooth permission.
     */
    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private companion object {
        const val UNLOCK_PROMPT = "Unlock openplayer"
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
