package com.musicplayer.app.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OutputDevice { SPEAKER, BLUETOOTH }

/**
 * Bluetooth output types that can actually carry music. SCO is deliberately excluded: it is the
 * low-bandwidth voice-call profile, and the platform lists an SCO route whenever Bluetooth is
 * merely switched on - so treating it as "Bluetooth headphones are connected" made the app show a
 * Bluetooth output with nothing paired.
 */
private val BLUETOOTH_MEDIA_TYPES = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)

/**
 * Output types whose arrival or departure is a deliberate physical act by the user, and therefore
 * the only ones that should pause playback. Everything else the platform enumerates - builtin
 * speaker and earpiece, telephony, remote submix, FM tuner, HDMI, aux lines - comes and goes as
 * the audio HAL reconfigures itself, including while an AudioTrack is being opened, which is
 * exactly when playback is starting.
 */
private val EXTERNAL_OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
)

/**
 * Tracks available audio outputs and lets the user switch between speaker and Bluetooth.
 * Auto-switches to Bluetooth the moment a media-capable one connects, and per spec, playback
 * pauses when the set of available outputs changes (a headphone/Bluetooth connect or disconnect).
 *
 * That pause is scoped to [EXTERNAL_OUTPUT_TYPES] by identity rather than to the raw total device
 * count: on real hardware the platform re-enumerates its internal output devices as ExoPlayer
 * opens its AudioTrack, so a total-count comparison fired the moment playback started and paused
 * the song instantly, every time.
 *
 * Routing is expressed by handing the chosen device to the player through [onPreferredDeviceChanged]
 * (ExoPlayer's preferred-audio-device API). It deliberately does not touch
 * `AudioManager.setCommunicationDevice`, which requests the *voice call* routing path - using it
 * for music forced the stream onto a comms route, which both silenced playback and left the player
 * suppressed as though the audio route were unusable.
 */
class OutputDeviceManager(
    private val context: Context,
    private val onExternalOutputsChanged: () -> Unit,
    private val onPreferredDeviceChanged: (AudioDeviceInfo?) -> Unit = {},
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _currentDevice = MutableStateFlow(OutputDevice.SPEAKER)
    val currentDevice: StateFlow<OutputDevice> = _currentDevice.asStateFlow()

    private val _bluetoothConnected = MutableStateFlow(false)
    val bluetoothConnected: StateFlow<Boolean> = _bluetoothConnected.asStateFlow()

    private var lastExternalOutputs: Set<Int>? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh(devicesChanged = true)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh(devicesChanged = true)
        }
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, null)
        refresh(devicesChanged = false)
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    private fun refresh(devicesChanged: Boolean) {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothPresent = outputs.any { it.type in BLUETOOTH_MEDIA_TYPES }
        _bluetoothConnected.value = bluetoothPresent
        if (bluetoothPresent && _currentDevice.value != OutputDevice.BLUETOOTH) {
            switchTo(OutputDevice.BLUETOOTH)
        } else if (!bluetoothPresent && _currentDevice.value == OutputDevice.BLUETOOTH) {
            switchTo(OutputDevice.SPEAKER)
        }

        // Identity, not count: two different headsets swapping in one callback is still a change
        // the user made, while the same set reported twice is just the HAL re-enumerating.
        val externalOutputs = outputs.filter { it.type in EXTERNAL_OUTPUT_TYPES }.map { it.id }.toSet()
        val previous = lastExternalOutputs
        lastExternalOutputs = externalOutputs
        if (devicesChanged && previous != null && previous != externalOutputs) {
            onExternalOutputsChanged()
        }
    }

    fun switchTo(device: OutputDevice) {
        _currentDevice.value = device
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val target = outputs.firstOrNull {
            when (device) {
                OutputDevice.BLUETOOTH -> it.type in BLUETOOTH_MEDIA_TYPES
                OutputDevice.SPEAKER -> it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
        }
        // Null is meaningful: it hands routing back to the platform's own default choice.
        onPreferredDeviceChanged(target)
    }
}
