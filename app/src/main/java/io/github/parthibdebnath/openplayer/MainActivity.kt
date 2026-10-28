package io.github.parthibdebnath.openplayer

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.parthibdebnath.openplayer.ui.AppRoot
import io.github.parthibdebnath.openplayer.ui.theme.OpenPlayerTheme
import io.github.parthibdebnath.openplayer.util.AuthGateManager
import io.github.parthibdebnath.openplayer.util.BrightnessVolumeController
import io.github.parthibdebnath.openplayer.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    // The same instance the composables get from viewModel(): both are scoped to this activity.
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goFullscreen()
        // Once per fresh start, not on every recreation, so a dialog dismissed by rotating the
        // screen isn't immediately shown again.
        if (savedInstanceState == null) requestRuntimePermissions()
        // Only on a fresh start: after recreation (or process death) the same intent comes back,
        // and replaying it would restart the opened song over whatever was playing since.
        if (savedInstanceState == null) handleOpenIntent(intent)
        val authGateManager = AuthGateManager(this)
        val brightnessController = BrightnessVolumeController(this)

        setContent {
            OpenPlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    val viewModel = mainViewModel
                    val requireAuth by viewModel.requireAuthToOpen.collectAsStateWithLifecycle()
                    val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()
                    // Deliberately not saved across recreation: restoring "unlocked" after the
                    // process was killed in the background would skip the lock entirely.
                    var unlocked by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    fun tryUnlock() {
                        scope.launch { unlocked = authGateManager.authenticate(UNLOCK_PROMPT) }
                    }

                    // Coming back after a while counts as opening the app again: with the lock on, it
                    // locks once the app has been in the background longer than a short grace
                    // period, rather than only on a cold start. The grace period covers quick trips
                    // out, such as to the folder picker.
                    var lockGeneration by remember { mutableIntStateOf(0) }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        var stoppedAt = 0L
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_STOP -> stoppedAt = SystemClock.elapsedRealtime()
                                Lifecycle.Event.ON_START -> {
                                    val away = SystemClock.elapsedRealtime() - stoppedAt
                                    if (requireAuth == true && unlocked && stoppedAt != 0L && away > RELOCK_AFTER_MS) {
                                        unlocked = false
                                        lockGeneration++
                                    }
                                }
                                else -> Unit
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    // With the lock on, the recent-apps thumbnail would still show the library to
                    // anyone holding the phone. FLAG_SECURE would hide it too, but would also block
                    // the owner's own screenshots, so this narrower switch is used where it exists.
                    LaunchedEffect(requireAuth) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            setRecentsScreenshotEnabled(requireAuth != true)
                        }
                    }

                    LaunchedEffect(requireAuth, lockGeneration) {
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
                        // The same goes for the rest of the settings: drawn with the defaults, the
                        // first frame showed the wrong theme for anyone who'd changed it.
                        requireAuth == null || !settingsLoaded ->
                            Box(Modifier.fillMaxSize().background(Color.Black))
                        unlocked -> AppRoot(authGateManager = authGateManager, brightnessController = brightnessController)
                        else -> LockedScreen(onRetry = ::tryUnlock)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    /** "Open with" from a file manager or another app: play that one file. */
    private fun handleOpenIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        mainViewModel.requestOpen(uri)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private companion object {
        const val UNLOCK_PROMPT = "Unlock openplayer"
        const val RELOCK_AFTER_MS = 2 * 60 * 1000L
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
