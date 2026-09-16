package com.musicplayer.app.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Wraps device screen-lock authentication (biometric or PIN/pattern/password fallback) for the
 * "Require Authorization to open/delete" settings. A 5-minute override window is offered
 * specifically for re-toggling the authorization settings themselves after one successful
 * unlock this session, per spec - it never applies to the open/delete gates themselves.
 */
class AuthGateManager(private val activity: FragmentActivity) {
    private var settingsOverrideUntilMs: Long = 0L

    fun canAuthenticate(): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun isSettingsAuthOverrideActive(): Boolean = System.currentTimeMillis() < settingsOverrideUntilMs

    fun startSettingsAuthOverride() {
        settingsOverrideUntilMs = System.currentTimeMillis() + SETTINGS_OVERRIDE_DURATION_MS
    }

    suspend fun authenticate(title: String, subtitle: String? = null): Boolean {
        if (!canAuthenticate()) return true // no lock screen configured on this device; nothing to gate on
        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
            val infoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
            subtitle?.let { infoBuilder.setSubtitle(it) }
            prompt.authenticate(infoBuilder.build())
        }
    }

    companion object {
        private const val SETTINGS_OVERRIDE_DURATION_MS = 5 * 60 * 1000L
    }
}
