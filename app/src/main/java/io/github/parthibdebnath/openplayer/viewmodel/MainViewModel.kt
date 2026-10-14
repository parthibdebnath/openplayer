package io.github.parthibdebnath.openplayer.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.parthibdebnath.openplayer.audio.OutputDevice
import io.github.parthibdebnath.openplayer.audio.PlayOrder
import io.github.parthibdebnath.openplayer.audio.PlaybackEngine
import io.github.parthibdebnath.openplayer.audio.PlaybackUiState
import io.github.parthibdebnath.openplayer.audio.RepeatMode
import io.github.parthibdebnath.openplayer.audio.SleepTimerState
import io.github.parthibdebnath.openplayer.data.model.Playlist
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.data.repository.EXTERNAL_LABEL
import io.github.parthibdebnath.openplayer.data.repository.ExternalSongs
import io.github.parthibdebnath.openplayer.data.repository.PlaylistRepository
import io.github.parthibdebnath.openplayer.data.repository.REMOVED_SONGS_FOLDER_NAME
import io.github.parthibdebnath.openplayer.data.model.SongSort
import io.github.parthibdebnath.openplayer.data.repository.isExternal
import io.github.parthibdebnath.openplayer.playback.PlaybackService
import io.github.parthibdebnath.openplayer.data.settings.EffectsRepository
import io.github.parthibdebnath.openplayer.data.settings.EffectsState
import io.github.parthibdebnath.openplayer.data.settings.PitchEffectState
import io.github.parthibdebnath.openplayer.data.settings.ReverbEffectState
import io.github.parthibdebnath.openplayer.data.settings.ReverbPreset
import io.github.parthibdebnath.openplayer.data.settings.CROSSFADE_MIN_MS
import io.github.parthibdebnath.openplayer.data.settings.SettingsRepository
import io.github.parthibdebnath.openplayer.data.settings.SettingsSnapshot
import io.github.parthibdebnath.openplayer.data.settings.SpeedEffectState
import io.github.parthibdebnath.openplayer.data.settings.ThreeDEffectState
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val playlistRepository = PlaylistRepository.get(application)
    val settingsRepository = SettingsRepository(application)
    private val effectsRepository = EffectsRepository(application)
    val playbackEngine = PlaybackEngine.get(application)

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Songs of the playlist currently being viewed, cached in the ViewModel rather than queried
     * fresh by the screen.
     *
     * Building the flow inside the screen meant leaving the File tab tore the subscription down,
     * and coming back re-ran the query and rendered an empty list until it landed - a full
     * re-query plus two layout passes over a few thousand rows on every tab switch. Held here it
     * survives the screen, and `WhileSubscribed` keeps it warm long enough to cover switching away
     * and back.
     */
    private val viewedPlaylistId = MutableStateFlow<Long?>(null)

    val viewedSongs: StateFlow<List<Song>> = viewedPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else playlistRepository.observeSongs(id)
        }
        // Sorted here, off the main thread, in the order the user picked - "play in order" then
        // plays exactly what the list shows.
        .combine(settingsRepository.songSort) { songs, sort -> sort.sort(songs) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), emptyList())

    fun onViewPlaylist(playlistId: Long?) {
        viewedPlaylistId.value = playlistId
    }

    /** True while a folder is being indexed; the song list says so rather than looking empty. */
    val scanning: StateFlow<Boolean> = playlistRepository.scanning

    val playbackUiState: StateFlow<PlaybackUiState> = playbackEngine.uiState

    // Loaded eagerly, so the SFX screen opens on the saved values rather than flashing defaults.
    val effectsState: StateFlow<EffectsState> = effectsRepository.effectsState
        .stateIn(viewModelScope, SharingStarted.Eagerly, EffectsState())

    val sleepTimerState: StateFlow<SleepTimerState> = playbackEngine.sleepTimerManager.state
    val outputDevice: StateFlow<OutputDevice> = playbackEngine.outputDeviceManager.currentDevice
    val bluetoothConnected: StateFlow<Boolean> = playbackEngine.outputDeviceManager.bluetoothConnected

    // Eagerly, so the value is already current whenever something reads it outside composition
    // - the background-playback check when the app is left, for one.
    val settingsSnapshot: StateFlow<SettingsSnapshot> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsSnapshot())

    /**
     * File/SAF operations can throw for all sorts of provider-specific reasons. A bare
     * `viewModelScope.launch { ... }` would let any of those crash the whole app with nothing
     * shown to the user - this logs instead, so a failed operation reads as "nothing happened"
     * rather than a silent force-close.
     */
    private val mutationExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MainViewModel", "File/playlist operation failed", throwable)
    }

    /**
     * Whether the user must unlock before seeing the app; null until the setting has been read.
     * Reading it through [settingsSnapshot] instead started from that snapshot's default of
     * "off", which showed the whole app for a moment before the lock caught up.
     */
    val requireAuthToOpen: StateFlow<Boolean?> = settingsRepository.requireAuthToOpen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(mutationExceptionHandler) {
            runCatching { settingsRepository.prepare() }
            // Restored before the rescan rather than after it: the saved songs are already in the
            // database, and a large library can take a while to rescan. The engine itself makes
            // sure this only happens once per process, so recreating the UI while music plays in
            // the background doesn't yank playback back to the last saved point.
            val restartSong = settingsRepository.restartSongOnOpen.first()
            playbackEngine.restoreIfNeeded(
                lookup = { ids -> playlistRepository.getSongsByIds(ids) },
                restartFromBeginning = restartSong,
            )
            if (restartSong) playbackEngine.restartCurrentIfPaused()
            playlistRepository.rescanAll()
            // Anything the rescan found gone - deleted, moved or renamed outside the app - comes
            // out of the queue now, rather than failing when playback reaches it.
            // Songs opened from another app have no row to find, and are left alone.
            val existing = playlistRepository.getAllSongIds()
            playbackEngine.dropSongs { !it.isExternal && it.id !in existing }
        }
        // Polled rather than scheduled for midnight: a minute's granularity is plenty for a
        // once-a-day colour, and it picks up correctly after the app has been closed over midnight.
        // Only while the app is on screen - nobody sees the colour otherwise, and it used to poll
        // in the background for as long as the app stayed in memory.
        viewModelScope.launch {
            while (true) {
                appVisible.first { it }
                if (settingsSnapshot.value.dailyRandomBackground) {
                    runCatching { settingsRepository.applyDailyRandomColorIfDue(todayStamp()) }
                }
                delay(DAILY_COLOR_CHECK_INTERVAL_MS)
            }
        }
        viewModelScope.launch {
            // The stored flow, not the StateFlow above: that one starts from a placeholder, and
            // applying it would switch effects off for a moment whenever the UI is recreated
            // while music plays in the background.
            effectsRepository.effectsState.collect { playbackEngine.applyEffects(it) }
        }
        viewModelScope.launch {
            settingsRepository.shuffleSeed
                .filter { it.isNotEmpty() }
                .collect { playbackEngine.setShuffleSeed(it) }
        }
        viewModelScope.launch {
            settingsRepository.crossfadeMs.collect { playbackEngine.crossfadeMs = it }
        }
        viewModelScope.launch {
            // distinctUntilChanged matters: uiState re-emits on every position tick, and without
            // it the service was asked to start/stop on each one.
            // Keyed off "a song is loaded", not "audio is playing": tying it to isPlaying tore the
            // media session down the moment you hit pause, taking the notification with it and
            // leaving nothing to resume from.
            combine(settingsRepository.notificationMediaControl, playbackEngine.uiState) { enabled, state ->
                enabled to (state.currentSong != null)
            }
                .distinctUntilChanged()
                .collect { (enabled, hasSong) -> updatePlaybackServiceState(enabled, hasSong) }
        }
    }

    private var playbackServiceRunning = false

    /** Whether the app is on screen; see [onAppForegrounded] and [onAppBackgrounded]. */
    private val appVisible = MutableStateFlow(false)

    /** The installed version, for the About section. */
    val appVersion: String = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val DAILY_COLOR_CHECK_INTERVAL_MS = 60_000L
    }

    /**
     * Whether we're allowed to post the playback notification. Media3 drives the media session
     * service off its notification, and on Android 13+ a denied POST_NOTIFICATIONS permission
     * means that notification can never be shown - which takes the session down with it and stops
     * playback. Playback itself has no need of the service, so when the permission isn't there we
     * simply don't start it and the music plays without system media controls.
     */
    private fun canPostNotifications(): Boolean {
        val app = getApplication<Application>()
        // Both halves matter: the runtime permission can be granted while the user has still
        // switched the app's notifications off in system settings, and either one alone is enough
        // to leave the media controls invisible.
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val _notificationsBlocked = MutableStateFlow(false)

    /** True when media controls are switched on but the system won't let us post the notification. */
    val notificationsBlocked: StateFlow<Boolean> = _notificationsBlocked.asStateFlow()

    private val _batteryOptimized = MutableStateFlow(false)

    /**
     * True when the system may restrict the app in the background. Some manufacturers then freeze
     * it a few minutes after the screen goes off, which cuts background playback short.
     */
    val batteryOptimized: StateFlow<Boolean> = _batteryOptimized.asStateFlow()

    private fun isBatteryOptimized(): Boolean {
        val app = getApplication<Application>()
        val powerManager = app.getSystemService(PowerManager::class.java) ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(app.packageName)
    }

    /**
     * Opens the system's battery optimization list, where the user can exempt the app themselves.
     * Asking for the exemption directly needs a permission that store policy reserves for apps
     * that can't work any other way, which a media player with a foreground service is not.
     */
    fun openBatteryOptimizationSettings() {
        val app = getApplication<Application>()
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
            .onFailure { Log.w("MainViewModel", "Could not open battery optimization settings", it) }
    }

    /** Re-checks notification and battery access, either of which can change while the app is away. */
    fun onAppForegrounded() {
        appVisible.value = true
        _batteryOptimized.value = isBatteryOptimized()
        val blocked = !canPostNotifications()
        _notificationsBlocked.value = blocked
        // Recover automatically if access was granted from system settings while we were away.
        if (!blocked && !playbackServiceRunning) {
            updatePlaybackServiceState(
                settingsSnapshot.value.notificationMediaControl,
                playbackUiState.value.currentSong != null,
            )
        }
    }

    /** Opens this app's system notification settings, the only place a denial can be undone. */
    fun openNotificationSettings() {
        val app = getApplication<Application>()
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
            .onFailure { Log.w("MainViewModel", "Could not open notification settings", it) }
    }

    /**
     * Starts/stops the MediaSessionService wrapping our player. Disabling "Notification Media
     * Control" removes the system notification entirely (per spec) - core playback in-app keeps
     * working either way since it talks to [PlaybackEngine] directly, not through this service.
     *
     * Uses `startService`, not `startForegroundService`: this only ever runs while the app is in
     * the foreground and already playing, so the start is permitted, Media3 promotes the service
     * to the foreground itself once it posts its notification, and no five-second
     * foreground-start deadline (whose expiry killed playback) applies.
     */
    private fun updatePlaybackServiceState(notificationEnabled: Boolean, hasSong: Boolean) {
        val app = getApplication<Application>()
        val canPost = canPostNotifications()
        val shouldRun = notificationEnabled && hasSong && canPost
        if (notificationEnabled && hasSong && !canPost) {
            Log.w("MainViewModel", "Media controls suppressed: notifications are blocked for this app")
        }
        if (shouldRun && !playbackServiceRunning) {
            runCatching {
                app.startService(Intent(app, PlaybackService::class.java))
                Log.i("MainViewModel", "Playback service started for media controls")
            }.onFailure { Log.w("MainViewModel", "Could not start playback service", it) }
            playbackServiceRunning = true
        } else if (!shouldRun && playbackServiceRunning) {
            runCatching { app.stopService(Intent(app, PlaybackService::class.java)) }
            playbackServiceRunning = false
        }
    }

    private fun launchSafely(block: suspend () -> Unit) =
        viewModelScope.launch(mutationExceptionHandler) { block() }

    // --- Playlist / file management -----------------------------------------------------

    fun createPlaylist(folderUri: Uri, name: String) = launchSafely {
        if (playlists.value.any { it.folderUri == folderUri.toString() }) {
            toast("That folder is already a playlist")
            return@launchSafely
        }
        playlistRepository.createPlaylist(folderUri, name)
    }

    /** Re-reads a playlist's folder now, rather than waiting for the next launch. */
    fun rescanPlaylist(playlistId: Long) = launchSafely {
        playlistRepository.rescanPlaylist(playlistId)
        val existing = playlistRepository.getAllSongIds()
        playbackEngine.dropSongs { !it.isExternal && it.playlistId == playlistId && it.id !in existing }
    }

    fun renamePlaylist(playlistId: Long, newName: String) = launchSafely {
        playlistRepository.renamePlaylist(playlistId, newName)
    }

    /** Unlinks the playlist from the app only; files on disk are untouched. */
    fun deletePlaylist(playlistId: Long) = launchSafely {
        // Out of the queue first: its songs are about to stop existing, and playback reaching one
        // would fail.
        playbackEngine.dropSongs { it.playlistId == playlistId }
        playlistRepository.deletePlaylist(playlistId)
    }

    fun renameSong(songId: Long, newName: String) = launchSafely {
        playlistRepository.renameSong(songId, newName)
        // The queue holds its own copies of songs; without this the player kept showing the old
        // name until the song was played again from the list.
        playbackEngine.updateSong(songId) { it.copy(displayName = newName) }
    }

    fun addSongs(playlistId: Long, sourceUris: List<Uri>) = launchSafely {
        if (sourceUris.isEmpty()) return@launchSafely
        val added = playlistRepository.addSongsFromDocuments(playlistId, sourceUris)
        when {
            added == sourceUris.size -> toast(if (added == 1) "Song added" else "$added songs added")
            added == 0 -> toast("Couldn't add ${if (sourceUris.size == 1) "that file" else "those files"} to this folder")
            else -> toast("$added of ${sourceUris.size} songs added")
        }
    }

    fun removeSong(song: Song) = launchSafely {
        if (playlistRepository.removeSong(song)) {
            playbackEngine.dropSongs { it.id == song.id }
        } else {
            // Previously a failed move simply did nothing, which looked like the tap was missed.
            toast("Couldn't move \"${song.displayName}\" to $REMOVED_SONGS_FOLDER_NAME")
        }
    }

    fun setSongSort(sort: SongSort) = viewModelScope.launch { settingsRepository.setSongSort(sort) }

    // --- Opening a file from another app ------------------------------------------------------

    private val _pendingOpen = MutableStateFlow<Uri?>(null)

    /**
     * A file another app asked us to play, waiting until the app is unlocked - opening one
     * shouldn't get round the lock, or replace the queue behind it.
     */
    val pendingOpen: StateFlow<Uri?> = _pendingOpen.asStateFlow()

    fun requestOpen(uri: Uri) {
        _pendingOpen.value = uri
    }

    /** Plays the pending file on its own, replacing the queue. */
    fun playPendingOpen() = launchSafely {
        val uri = _pendingOpen.value ?: return@launchSafely
        _pendingOpen.value = null
        val song = ExternalSongs.resolve(getApplication(), uri)
        if (song == null) {
            _openFailed.value = true
            return@launchSafely
        }
        playbackEngine.playSongs(listOf(song), EXTERNAL_LABEL, PlayOrder.SEQUENTIAL)
    }

    private val _openFailed = MutableStateFlow(false)

    /** True after a file from another app couldn't be opened, until the message is dismissed. */
    val openFailed: StateFlow<Boolean> = _openFailed.asStateFlow()

    fun dismissOpenFailed() {
        _openFailed.value = false
    }

    // --- Playback --------------------------------------------------------------------------

    fun playPlaylistInOrder(songs: List<Song>, label: String) {
        playbackEngine.playSongs(songs, label, PlayOrder.SEQUENTIAL)
    }

    /** Each press produces a genuinely new shuffle - unless the seed is locked, which is the point of locking it. */
    fun playPlaylistShuffled(songs: List<Song>, label: String) = viewModelScope.launch {
        if (!settingsSnapshot.value.shuffleSeedLocked) {
            playbackEngine.setShuffleSeed(settingsRepository.regenerateShuffleSeed())
        }
        playbackEngine.playSongs(songs, label, PlayOrder.SHUFFLED)
    }

    fun playSpecificSong(songs: List<Song>, label: String, song: Song) {
        playbackEngine.playSpecificSong(songs, label, song)
    }

    /** Plays several playlists as one combined queue, labeled "Multi-Playlist" (per spec). */
    fun playMultiPlaylist(playlistIds: List<Long>, shuffled: Boolean) = launchSafely {
        play(orderedSongs(playlistIds), "Multi-Playlist", shuffled)
    }

    /** Plays a whole playlist straight from the playlist list, without opening it. */
    fun playPlaylist(playlistId: Long, shuffled: Boolean) = launchSafely {
        val name = playlists.value.firstOrNull { it.id == playlistId }?.displayName.orEmpty()
        play(orderedSongs(listOf(playlistId)), name, shuffled)
    }

    private fun play(songs: List<Song>, label: String, shuffled: Boolean) {
        if (songs.isEmpty()) {
            toast("No songs to play yet")
            return
        }
        if (shuffled) playPlaylistShuffled(songs, label) else playPlaylistInOrder(songs, label)
    }

    /**
     * Songs of [playlistIds], playlist by playlist in the order they're listed, each one sorted the
     * way playlists are being shown - so playing in order matches what the user sees.
     */
    private suspend fun orderedSongs(playlistIds: List<Long>): List<Song> {
        val songs = playlistRepository.getSongsForPlaylists(playlistIds)
        val sort = settingsSnapshot.value.songSort
        val listed = playlists.value.map { it.id }.filter { it in playlistIds }.ifEmpty { playlistIds }
        return withContext(Dispatchers.Default) {
            val byPlaylist = songs.groupBy { it.playlistId }
            listed.flatMap { id -> sort.sort(byPlaylist[id].orEmpty()) }
        }
    }

    fun queueSong(song: Song) = playbackEngine.queueSong(song)
    fun playNext(song: Song) = playbackEngine.playNext(song)
    fun togglePlayPause() = playbackEngine.togglePlayPause()
    fun skipNext() = playbackEngine.skipToNext()
    fun skipPrevious() = playbackEngine.skipToPrevious()
    fun seekTo(positionMs: Long) = playbackEngine.seekTo(positionMs)
    fun replaceUpcomingSlot(index: Int) = playbackEngine.replaceUpcomingSlot(index)
    fun reorderUpcoming(from: Int, to: Int) = playbackEngine.reorderUpcoming(from, to)

    fun setRepeatMode(mode: RepeatMode) = playbackEngine.setRepeatMode(mode)

    fun cycleRepeatMode() {
        val next = when (playbackEngine.queueManager.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(next)
    }

    fun setShuffleEnabled(enabled: Boolean) = playbackEngine.setShuffleEnabled(enabled)

    fun toggleShuffle() = setShuffleEnabled(playbackUiState.value.shuffleOn.not())

    fun switchOutputDevice(device: OutputDevice) = playbackEngine.outputDeviceManager.switchTo(device)

    // --- Shuffle seed (settings) -------------------------------------------------------------

    fun setShuffleSeedText(seed: String) = viewModelScope.launch {
        settingsRepository.setShuffleSeed(seed)
    }

    fun refreshShuffleSeed() = viewModelScope.launch {
        val newSeed = settingsRepository.regenerateShuffleSeed()
        playbackEngine.refreshShuffleSeed(newSeed)
    }

    fun setShuffleSeedLocked(locked: Boolean) = viewModelScope.launch {
        settingsRepository.setShuffleSeedLocked(locked)
    }

    // --- Settings toggles ----------------------------------------------------------------

    fun setNotificationMediaControl(value: Boolean) = viewModelScope.launch { settingsRepository.setNotificationMediaControl(value) }
    fun setRequireAuthToOpen(value: Boolean) = viewModelScope.launch { settingsRepository.setRequireAuthToOpen(value) }
    fun setRequireAuthToDelete(value: Boolean) = viewModelScope.launch { settingsRepository.setRequireAuthToDelete(value) }
    fun setTwoFingerBrightnessGesture(value: Boolean) = viewModelScope.launch { settingsRepository.setTwoFingerBrightnessGesture(value) }
    fun setThreeFingerVolumeGesture(value: Boolean) = viewModelScope.launch { settingsRepository.setThreeFingerVolumeGesture(value) }
    fun setGlassTheming(value: Boolean) = viewModelScope.launch { settingsRepository.setGlassTheming(value) }
    fun setTwentyFourHourClock(value: Boolean) = viewModelScope.launch { settingsRepository.setTwentyFourHourClock(value) }
    fun setCrossfadeMs(value: Int) = viewModelScope.launch { settingsRepository.setCrossfadeMs(value) }
    fun setBackgroundPlayback(value: Boolean) = viewModelScope.launch { settingsRepository.setBackgroundPlayback(value) }
    fun setBackgroundColorHex(value: String) = viewModelScope.launch { settingsRepository.setBackgroundColorHex(value) }
    fun setHideIconBackgrounds(value: Boolean) = viewModelScope.launch { settingsRepository.setHideIconBackgrounds(value) }
    fun setScrollLongTitles(value: Boolean) = viewModelScope.launch { settingsRepository.setScrollLongTitles(value) }
    fun setRestartSongOnOpen(value: Boolean) = viewModelScope.launch { settingsRepository.setRestartSongOnOpen(value) }

    fun setDailyRandomBackground(value: Boolean) = viewModelScope.launch {
        settingsRepository.setDailyRandomBackground(value)
        // Turning it on takes effect immediately rather than at the next midnight.
        if (value) settingsRepository.applyDailyRandomColorIfDue(todayStamp())
    }

    private fun todayStamp(): String =
        java.time.LocalDate.now().toString()

    /** Called when the app leaves the foreground; stops the music if background playback is off. */
    fun onAppBackgrounded() {
        appVisible.value = false
        if (!settingsSnapshot.value.backgroundPlayback) playbackEngine.pause()
        playbackEngine.persistNow()
    }

    // --- Effects ---------------------------------------------------------------------------

    fun setGlobalEffectsEnabled(enabled: Boolean) = updateEffects { it.copy(globalEnabled = enabled) }

    fun updateSpeed(transform: (SpeedEffectState) -> SpeedEffectState) = updateEffects { it.copy(speed = transform(it.speed)) }
    fun updatePitch(transform: (PitchEffectState) -> PitchEffectState) = updateEffects { it.copy(pitch = transform(it.pitch)) }
    fun updateReverb(transform: (ReverbEffectState) -> ReverbEffectState) = updateEffects { it.copy(reverb = transform(it.reverb)) }
    fun updateThreeD(transform: (ThreeDEffectState) -> ThreeDEffectState) = updateEffects { it.copy(threeD = transform(it.threeD)) }

    fun applyReverbPreset(preset: ReverbPreset) = updateEffects {
        it.copy(reverb = ReverbEffectState.fromPreset(preset, enabled = it.reverb.enabled))
    }

    private fun updateEffects(transform: (EffectsState) -> EffectsState) = viewModelScope.launch {
        effectsRepository.update(transform)
    }

    // --- Sleep timer -------------------------------------------------------------------------

    fun sleepTimerPrefill(): Pair<Int, Int> = playbackEngine.sleepTimerManager.remainingHoursMinutesForPrefill()

    fun confirmSleepTimer(hours: Int, minutes: Int, playLastSongToEnd: Boolean) {
        playbackEngine.sleepTimerManager.confirm(hours, minutes, playLastSongToEnd)
    }

    fun cancelSleepTimer() = playbackEngine.sleepTimerManager.cancel()
}
