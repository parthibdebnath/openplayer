package io.github.parthibdebnath.openplayer.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps device screen-lock authentication (biometric or PIN/pattern/password fallback) for the
 * "Require Authorization to open/delete" settings. A 5-minute override window is offered
 * specifically for re-toggling the authorization settings themselves after one successful
 * unlock this session, per spec - it never applies to the open/delete gates themselves.
 *
 * Built on the platform's own prompt rather than androidx.biometric, which brought AppCompat and
 * Fragment into an all-Compose app purely to show a system dialog - roughly a quarter of a
 * megabyte of code and every one of AppCompat's translated resources. Must be created before the
 * activity is started, since it registers an activity result for the older-Android path.
 */
class AuthGateManager(private val activity: ComponentActivity) {
    private var settingsOverrideUntilMs: Long = 0L

    private val keyguard = activity.getSystemService(KeyguardManager::class.java)

    /** The credential screen in flight on Android 8-9, waiting on its result. */
    private var pendingCredential: CancellableContinuation<Boolean>? = null

    private val confirmCredential = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingCredential?.let { if (it.isActive) it.resume(result.resultCode == Activity.RESULT_OK) }
        pendingCredential = null
    }

    /** Whether the phone has a screen lock at all; with none there's nothing to authenticate against. */
    fun canAuthenticate(): Boolean = keyguard?.isDeviceSecure == true

    fun isSettingsAuthOverrideActive(): Boolean = SystemClock.elapsedRealtime() < settingsOverrideUntilMs

    fun startSettingsAuthOverride() {
        settingsOverrideUntilMs = SystemClock.elapsedRealtime() + SETTINGS_OVERRIDE_DURATION_MS
    }

    suspend fun authenticate(title: String, subtitle: String? = null): Boolean {
        if (!canAuthenticate()) return true // no lock screen configured on this device; nothing to gate on
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            authenticateWithPrompt(title, subtitle)
        } else {
            authenticateWithCredentialScreen(title, subtitle)
        }
    }

    /** Android 10+: the system biometric prompt, which falls back to the PIN, pattern or password itself. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun authenticateWithPrompt(title: String, subtitle: String?): Boolean =
        suspendCancellableCoroutine { continuation ->
            val builder = BiometricPrompt.Builder(activity).setTitle(title)
            subtitle?.let { builder.setSubtitle(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            val cancel = CancellationSignal()
            builder.build().authenticate(
                cancel,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
            // If whatever asked goes away first (its screen closes, the lock setting changes), the
            // system prompt goes with it instead of lingering over the app.
            continuation.invokeOnCancellation { runCatching { cancel.cancel() } }
        }

    /**
     * Android 8-9: the platform prompt there can't fall back to the screen lock, so the system's
     * own "confirm your PIN" screen is used instead, which accepts a fingerprint too where the
     * lock screen does.
     */
    private suspend fun authenticateWithCredentialScreen(title: String, subtitle: String?): Boolean {
        @Suppress("DEPRECATION")
        val intent = keyguard?.createConfirmDeviceCredentialIntent(title, subtitle) ?: return true
        return suspendCancellableCoroutine { continuation ->
            pendingCredential = continuation
            continuation.invokeOnCancellation { pendingCredential = null }
            runCatching { confirmCredential.launch(intent) }.onFailure {
                pendingCredential = null
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    companion object {
        private const val SETTINGS_OVERRIDE_DURATION_MS = 5 * 60 * 1000L
    }
}
