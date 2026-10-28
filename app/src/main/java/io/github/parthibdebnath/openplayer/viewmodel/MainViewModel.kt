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
import io.github.parthibdebnath.openplayer.playback.MediaButtonSession
import io.github.parthibdebnath.openplayer.playback.PlaybackService
import io.github.parthibdebnath.openplayer.data.settings.EffectsRepository
import io.github.parthibdebnath.openplayer.data.settings.EffectsState
import io.github.parthibdebnath.openplayer.data.settings.OtherAppSound
import io.github.parthibdebnath.openplayer.data.settings.HapticStrength
import io.github.parthibdebnath.openplayer.data.repository.ExportedPlaylist
import io.github.parthibdebnath.openplayer.data.repository.LibraryExport
import io.github.parthibdebnath.openplayer.data.repository.readExportText
import io.github.parthibdebnath.openplayer.data.model.FAVOURITES_LABEL
import io.github.parthibdebnath.openplayer.data.model.FAVOURITES_PLAYLIST_ID
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
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
import kotlinx.coroutines.flow.map
import io.github.parthibdebnath.openplayer.util.mapState
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val playlistRepository = PlaylistRepository.get(application)
    val settingsRepository = SettingsRepository(application)
    private val effectsRepository = EffectsRepository(application)
    val playbackEngine = PlaybackEngine.get(application)

    /**
     * Every playlist, or null until the first read has landed - see [libraryOverview], which is
     * what the playlist list reads. Loaded eagerly, so it's normally in hand long before the File
     * tab is opened.
     */
    private val playlistsOrNull: StateFlow<List<Playlist>?> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val playlists: StateFlow<List<Playlist>> = playlistsOrNull.mapState { it.orEmpty() }

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

    /**
     * Carries the id of the playlist its songs belong to. Opening another playlist kept showing
     * the last one's songs - usually the one playing - until the new query landed, so a screen
     * only shows these once the id matches its own.
     */
    val viewedSongs: StateFlow<ViewedSongs> = viewedPlaylistId
        .flatMapLatest { id ->
            when (id) {
                null -> flowOf(emptyList())
                FAVOURITES_PLAYLIST_ID -> playlistRepository.observeFavourites()
                else -> playlistRepository.observeSongs(id)
            }.map { songs -> ViewedSongs(id, songs) }
        }
        // Sorted here, off the main thread, in the order the user picked - "play in order" then
        // plays exactly what the list shows.
        .combine(settingsRepository.songSort) { viewed, sort -> viewed.copy(songs = sort.sort(viewed.songs)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000), ViewedSongs(null, emptyList()))

    fun onViewPlaylist(playlistId: Long?) {
        viewedPlaylistId.value = playlistId
    }

    /** Every favourite song, in no particular order; the list screen sorts what it shows. */
    private val favouritesOrNull: StateFlow<List<Song>?> = playlistRepository.observeFavourites()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val favourites: StateFlow<List<Song>> = favouritesOrNull.mapState { it.orEmpty() }

    /**
     * Playlists and favourites together, null until both have been read: the playlist list shows
     * nothing at all rather than a first frame that's then rearranged.
     */
    val libraryOverview: StateFlow<LibraryOverview?> = combine(playlistsOrNull, favouritesOrNull) { playlists, favourites ->
        // Favourites are waited for too, so the Favourites row doesn't drop in above the playlists
        // a moment after they appear.
        if (playlists == null || favourites == null) null else LibraryOverview(playlists, favourites)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Which songs are favourites, for hearts and menu labels. */
    val favouriteIds: StateFlow<Set<Long>> = favourites
        .map { list -> list.mapTo(HashSet(list.size)) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Plays every favourite, in the order the Favourites list shows them, or shuffled. */
    fun playFavourites(shuffled: Boolean) {
        val songs = settingsSnapshot.value.songSort.sort(favourites.value)
        if (shuffled) playPlaylistShuffled(songs, FAVOURITES_LABEL) else playPlaylistInOrder(songs, FAVOURITES_LABEL)
    }

    /** Hearts or un-hearts [song]. Files opened from other apps aren't in the library, so can't be. */
    fun toggleFavourite(song: Song) = launchSafely {
        if (song.isExternal) return@launchSafely
        playlistRepository.setFavourite(song.id, song.id !in favouriteIds.value)
        backupNudge.value++
    }

    /** True while a folder is being indexed; the song list says so rather than looking empty. */
    val scanning: StateFlow<Boolean> = playlistRepository.scanning

    val playbackUiState: StateFlow<PlaybackUiState> = playbackEngine.uiState

    /**
     * Just the song that's loaded. Screens that only mark the current song - the song and playlist
     * lists - read this instead of [playbackUiState], which changes with the position several times
     * a second and recomposed whole lists of thousands of songs on every tick.
     */
    val currentSong: StateFlow<Song?> = playbackEngine.uiState
        .map { it.currentSong }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Loaded eagerly, so the SFX screen opens on the saved values rather than flashing defaults.
    val effectsState: StateFlow<EffectsState> = effectsRepository.effectsState
        .stateIn(viewModelScope, SharingStarted.Eagerly, EffectsState())

    val sleepTimerState: StateFlow<SleepTimerState> = playbackEngine.sleepTimerManager.state
    val outputDevice: StateFlow<OutputDevice> = playbackEngine.outputDeviceManager.currentDevice
    val bluetoothConnected: StateFlow<Boolean> = playbackEngine.outputDeviceManager.bluetoothConnected

    // Eagerly, so the value is already current whenever something reads it outside composition
    // - the background-playback check when the app is left, for one.
    private val settingsOrNull: StateFlow<SettingsSnapshot?> = settingsRepository.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settingsSnapshot: StateFlow<SettingsSnapshot> = settingsOrNull.mapState { it ?: DEFAULT_SETTINGS }

    /**
     * Whether the saved settings have been read, rather than the defaults standing in for them.
     * The app isn't drawn before then: its first frame used the default theme and glass setting,
     * and changed a moment later for anyone who had picked something else.
     */
    val settingsLoaded: StateFlow<Boolean> = settingsOrNull.mapState { it != null }

    private val _playbackRestored = MutableStateFlow(false)

    /**
     * True once the queue saved last time has been put back (or there was none). Until then the
     * player shows no song rather than "Start a playlist", which flashed on every launch before
     * the saved song came back.
     */
    val playbackRestored: StateFlow<Boolean> = _playbackRestored.asStateFlow()

    /**
     * File/SAF operations can throw for all sorts of provider-specific reasons. A bare
     * `viewModelScope.launch { ... }` would let any of those crash the whole app with nothing
     * shown to the user - this logs instead, so a failed operation reads as "nothing happened"
     * rather than a silent force-close.
     */
    private val mutationExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MainViewModel", "Background operation failed", throwable)
    }

    /**
     * Whether the user must unlock before seeing the app; null until the setting has been read.
     * Reading it through [settingsSnapshot] instead started from that snapshot's default of
     * "off", which showed the whole app for a moment before the lock caught up.
     */
    val requireAuthToOpen: StateFlow<Boolean?> = settingsRepository.requireAuthToOpen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Whether the app is on screen; see [onAppForegrounded] and [onAppBackgrounded].
     * Declared ahead of `init`: viewModelScope runs on Main.immediate, so the daily-colour loop
     * there reads this during construction, and declared below it was still null - a crash on
     * every launch.
     */
    private val appVisible = MutableStateFlow(false)

    // Also ahead of `init`, for the same reason: the resume-position callback set up there uses
    // it, and nothing but luck kept the engine from calling back before it was assigned.
    // Everything `init` touches belongs above it.
    private val resumeWriter = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private val exportJson = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    /**
     * Bookkeeping for the backups kept in the playlist folders: this install's id, and the
     * backups from earlier installs that have already been restored or turned down. Deliberately
     * outside the settings, which Android's own backup can carry to a new install - this has to
     * start afresh with each one.
     */
    private val backupState = application.getSharedPreferences("folder_backup", android.content.Context.MODE_PRIVATE)

    private val installId: String = backupState.getString(KEY_INSTALL_ID, null)
        ?: java.util.UUID.randomUUID().toString().also { backupState.edit().putString(KEY_INSTALL_ID, it).apply() }

    /** Bumped by changes the playlist list doesn't show, such as a renamed song, to refresh the backups. */
    private val backupNudge = MutableStateFlow(0)

    private val _restoreOffer = MutableStateFlow<RestoreOffer?>(null)

    /** A backup from an earlier install, found in a folder just added, waiting for a yes or no. */
    val restoreOffer: StateFlow<RestoreOffer?> = _restoreOffer.asStateFlow()

    /** The last backup written, minus its timestamp, so an unchanged library isn't written again. */
    private var lastBackupWritten: String? = null

    init {
        // However the restore goes - a slow device, or a failure that ends the launch below - the
        // player isn't left blank for more than a moment.
        viewModelScope.launch {
            delay(RESTORE_PLACEHOLDER_LIMIT_MS)
            _playbackRestored.value = true
        }
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
            _playbackRestored.value = true
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
            settingsRepository.otherAppSound.collect { playbackEngine.otherAppSound = it }
        }
        viewModelScope.launch {
            settingsRepository.skipSilence.collect { playbackEngine.skipSilence = it }
        }
        viewModelScope.launch {
            settingsRepository.normalizeVolume.collect { playbackEngine.normalizeVolume = it }
        }
        viewModelScope.launch {
            settingsRepository.rememberLongTracks.collect { playbackEngine.rememberLongTracks = it }
        }
        // The engine has no database of its own; positions in long tracks are written from here.
        // Application-scoped like the engine, not the ViewModel's scope: the engine outlives the
        // screen while music plays in the background.
        playbackEngine.onResumePositionChanged = { id, position ->
            resumeWriter.launch { playlistRepository.saveResumePosition(id, position) }
        }
        viewModelScope.launch {
            // distinctUntilChanged matters: uiState re-emits on every position tick, and without
            // it the service was asked to start/stop on each one.
            // Keyed off "a song is loaded", not "audio is playing": tying it to isPlaying tore the
            // media session down the moment you hit pause, taking the notification with it and
            // leaving nothing to resume from.
            //
            // The service's real state is part of the key, not a flag remembering that it was
            // started: the system stops it by itself after a long pause in the background, and
            // a remembered "running" then kept it from ever being started again, so the
            // notification never came back for the rest of the session.
            combine(
                settingsRepository.notificationMediaControl,
                playbackEngine.uiState,
                PlaybackService.running,
                appVisible,
            ) { enabled, state, running, visible -> ServiceInputs(enabled, state.currentSong != null, running, visible) }
                .distinctUntilChanged()
                .collect { updatePlaybackServiceState(it) }
        }
        // The backups in the playlist folders follow the library and settings, a few seconds
        // after the last change rather than on every one: a slider dragged through the effects
        // would otherwise rewrite every folder's backup dozens of times.
        viewModelScope.launch(mutationExceptionHandler) {
            combine(playlists, settingsSnapshot, effectsRepository.effectsState, backupNudge, _restoreOffer) { list, settings, _, _, offer ->
                Triple(list.isNotEmpty(), settings.backupToFolders, offer == null)
            }
                .debounce(BACKUP_DELAY_MS)
                .collectLatest { (hasPlaylists, enabled, noOfferPending) ->
                    // Never while a restore is being offered: that would overwrite the very
                    // backup being offered with this install's empty library.
                    if (hasPlaylists && enabled && noOfferPending) writeFolderBackups()
                }
        }
    }

    private data class ServiceInputs(val enabled: Boolean, val hasSong: Boolean, val running: Boolean, val visible: Boolean)

    /** The installed version, for the About section. */
    val appVersion: String = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val DAILY_COLOR_CHECK_INTERVAL_MS = 60_000L
        const val BACKUP_DELAY_MS = 3_000L
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_SETTLED = "settled_backups"
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
        playbackEngine.uiVisible = true
        _batteryOptimized.value = isBatteryOptimized()
        // Access granted from system settings while we were away is picked up by the service
        // check, which re-runs now that the app is visible again.
        _notificationsBlocked.value = !canPostNotifications()
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
    private fun updatePlaybackServiceState(inputs: ServiceInputs) {
        val app = getApplication<Application>()
        val canPost = canPostNotifications()
        val shouldRun = inputs.enabled && inputs.hasSong && canPost
        if (inputs.enabled && inputs.hasSong && !canPost) {
            Log.w("MainViewModel", "Media controls suppressed: notifications are blocked for this app")
        }
        // Only started while the app is on screen: a background start isn't allowed, and would
        // throw. If the system stopped the service while we were away, coming back restarts it.
        if (shouldRun && !inputs.running && inputs.visible) {
            runCatching {
                app.startService(Intent(app, PlaybackService::class.java))
                Log.i("MainViewModel", "Playback service started for media controls")
            }.onFailure { Log.w("MainViewModel", "Could not start playback service", it) }
        } else if (!shouldRun && inputs.running) {
            runCatching { app.stopService(Intent(app, PlaybackService::class.java)) }
        }
        // Headset and media buttons work whether or not there's a notification: while the
        // service isn't running (or about to be started just above), a session of the app's own
        // takes them. See MediaButtonSession.
        val serviceTakesButtons = inputs.running || (shouldRun && inputs.visible)
        if (inputs.hasSong && !serviceTakesButtons) MediaButtonSession.start(app) else MediaButtonSession.stop()
    }

    private fun launchSafely(block: suspend () -> Unit) =
        viewModelScope.launch(mutationExceptionHandler) { block() }

    // --- Playlist / file management -----------------------------------------------------

    fun createPlaylist(folderUri: Uri, name: String) = launchSafely {
        if (playlists.value.any { it.folderUri == folderUri.toString() }) {
            toast("That folder is already a playlist")
            return@launchSafely
        }
        // Read before the playlist exists: once it does, this install's own backup starts to
        // replace the one already there.
        val earlier = restorableBackupIn(folderUri)
        playlistRepository.createPlaylist(folderUri, name)
        if (earlier != null) _restoreOffer.value = RestoreOffer(earlier, folderUri.toString(), name)
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
        backupNudge.value++
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

    fun setSongSort(sort: SongSort) = launchSafely { settingsRepository.setSongSort(sort) }

    fun setOtherAppSound(value: OtherAppSound) = launchSafely { settingsRepository.setOtherAppSound(value) }
    fun setSkipSilence(value: Boolean) = launchSafely { settingsRepository.setSkipSilence(value) }
    fun setNormalizeVolume(value: Boolean) = launchSafely { settingsRepository.setNormalizeVolume(value) }
    fun setRememberLongTracks(value: Boolean) = launchSafely { settingsRepository.setRememberLongTracks(value) }
    fun setHapticStrength(value: HapticStrength) = launchSafely { settingsRepository.setHapticStrength(value) }
    fun setKeepScreenOn(value: Boolean) = launchSafely { settingsRepository.setKeepScreenOn(value) }

    fun setIncludeSubfolders(playlistId: Long, include: Boolean) = launchSafely {
        playlistRepository.setIncludeSubfolders(playlistId, include)
        val existing = playlistRepository.getAllSongIds()
        playbackEngine.dropSongs { !it.isExternal && it.playlistId == playlistId && it.id !in existing }
    }

    // --- Search across playlists -----------------------------------------------------------

    private val searchQuery = MutableStateFlow("")

    /**
     * Matches for the library-wide search, newest query only; empty until something is typed.
     * Carries the query it answers: while a new one is still being looked up the screen keeps
     * describing the last answer, rather than saying "nothing matches" for a moment on each new
     * search. Clearing the box takes effect at once.
     */
    val searchResults: StateFlow<SearchResults> = searchQuery
        .debounce { query -> if (query.isBlank()) 0L else 120L }
        .mapLatest { query ->
            val trimmed = query.trim()
            SearchResults(trimmed, if (trimmed.isEmpty()) emptyList() else playlistRepository.search(trimmed))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults("", emptyList()))

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    /** Plays a search result in its own playlist, from that song on. */
    fun playSearchResult(song: Song) = launchSafely {
        val name = playlists.value.firstOrNull { it.id == song.playlistId }?.displayName.orEmpty()
        val songs = orderedSongs(listOf(song.playlistId))
        if (songs.isEmpty()) return@launchSafely
        playbackEngine.playSpecificSong(songs, name, songs.firstOrNull { it.id == song.id } ?: song)
    }

    // --- Export and import -------------------------------------------------------------------

    /** Writes every playlist to the file the user picked. */
    fun exportLibrary(target: Uri) = launchSafely {
        val app = getApplication<Application>()
        val export = currentBackup()
        val written = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openOutputStream(target, "wt")?.use { out ->
                    out.write(exportJson.encodeToString(LibraryExport.serializer(), export).toByteArray())
                } != null
            }.getOrDefault(false)
        }
        toast(
            when {
                !written -> "Couldn't write that file"
                export.playlists.size == 1 -> "1 playlist and your settings exported"
                else -> "${export.playlists.size} playlists and your settings exported"
            },
        )
    }

    private val _foldersToRepick = MutableStateFlow<List<ExportedPlaylist>>(emptyList())

    /**
     * Imported playlists whose folders the app can't read yet - after a reinstall, or on another
     * phone - waiting to be picked again, first one first.
     */
    val foldersToRepick: StateFlow<List<ExportedPlaylist>> = _foldersToRepick.asStateFlow()

    /** Reads an exported file and recreates its playlists. */
    fun importLibrary(source: Uri) = launchSafely {
        val app = getApplication<Application>()
        val export = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(source)?.use { input ->
                    input.readExportText()?.let { exportJson.decodeFromString(LibraryExport.serializer(), it) }
                }
            }.getOrNull()
        }
        if (export == null) {
            toast("That isn't an openplayer playlist file")
            return@launchSafely
        }
        restoreSettings(export)
        var imported = 0
        val repick = ArrayList<ExportedPlaylist>()
        for (playlist in export.playlists) {
            val uri = Uri.parse(playlist.folderUri)
            when {
                playlistRepository.hasPlaylistFor(uri) -> Unit
                playlistRepository.canRead(uri) -> {
                    playlistRepository.importPlaylist(playlist, uri)
                    imported++
                }
                else -> repick += playlist
            }
        }
        _foldersToRepick.value = repick
        toast(
            when {
                repick.isNotEmpty() -> "$imported imported. Pick the folder for each of the other ${repick.size}."
                imported == 0 -> "Those playlists are already here"
                imported == 1 -> "1 playlist imported"
                else -> "$imported playlists imported"
            },
        )
    }

    /** The folder picked for the first playlist waiting on one, or null if that one was skipped. */
    fun onFolderRepicked(folder: Uri?) = launchSafely {
        val waiting = _foldersToRepick.value
        val playlist = waiting.firstOrNull() ?: return@launchSafely
        _foldersToRepick.value = waiting.drop(1)
        if (folder != null) playlistRepository.importPlaylist(playlist, folder)
    }

    // --- Backups kept in the playlist folders --------------------------------------------------

    /** The library, settings and effects as they are now, ready to save. */
    private suspend fun currentBackup(): LibraryExport = playlistRepository.exportLibrary().copy(
        settings = settingsRepository.exportAll(),
        effects = effectsRepository.effectsState.first(),
        installId = installId,
        savedAt = System.currentTimeMillis(),
    )

    /** Brings the backup in every playlist folder up to date, if anything has changed. */
    private suspend fun writeFolderBackups() {
        val backup = currentBackup()
        val comparable = exportJson.encodeToString(LibraryExport.serializer(), backup.copy(savedAt = 0L))
        if (comparable == lastBackupWritten) return
        val text = exportJson.encodeToString(LibraryExport.serializer(), backup)
        var written = 0
        for (playlist in backup.playlists) {
            val folder = Uri.parse(playlist.folderUri)
            if (!playlistRepository.canRead(folder)) continue
            if (playlistRepository.writeFolderBackup(folder, text)) {
                written++
            } else {
                Log.w("MainViewModel", "Couldn't write the backup in a playlist folder")
            }
        }
        if (written > 0) lastBackupWritten = comparable
    }

    /**
     * The backup in [folder], if it came from another install and hasn't been dealt with yet.
     * This install's own backups, and ones already restored or turned down, are left alone.
     */
    private suspend fun restorableBackupIn(folder: Uri): LibraryExport? {
        if (!settingsSnapshot.value.backupToFolders) return null
        val text = playlistRepository.readFolderBackup(folder) ?: return null
        val backup = runCatching { exportJson.decodeFromString(LibraryExport.serializer(), text) }.getOrNull() ?: return null
        val from = backup.installId ?: return null
        if (from == installId || from in settledBackups()) return null
        if (backup.playlists.isEmpty() && backup.settings.isNullOrEmpty()) return null
        return backup
    }

    private fun settledBackups(): Set<String> = backupState.getStringSet(KEY_SETTLED, emptySet()).orEmpty()

    private fun settle(backup: LibraryExport) {
        val from = backup.installId ?: return
        backupState.edit().putStringSet(KEY_SETTLED, settledBackups() + from).apply()
    }

    /** Puts back a backup's settings and sound effects, where it has them. */
    private suspend fun restoreSettings(backup: LibraryExport) {
        backup.settings?.takeIf { it.isNotEmpty() }?.let { settingsRepository.importAll(it) }
        backup.effects?.let { effects -> effectsRepository.update { effects } }
    }

    /** Restores the backup being offered: settings, effects and every playlist in it. */
    fun acceptRestore() = launchSafely {
        val offer = _restoreOffer.value ?: return@launchSafely
        val backup = offer.backup
        restoreSettings(backup)
        // The folder just added is replaced by the backup's version of it, with its old name,
        // subfolder setting and renamed songs.
        val added = Uri.parse(offer.folderUri)
        val inBackup = backup.playlists.any { it.folderUri == offer.folderUri }
        if (inBackup) playlistRepository.deletePlaylistFor(added)
        var restored = if (inBackup) 0 else 1
        val repick = ArrayList<ExportedPlaylist>()
        for (playlist in backup.playlists) {
            val uri = Uri.parse(playlist.folderUri)
            when {
                playlist.folderUri == offer.folderUri -> {
                    playlistRepository.importPlaylist(playlist, uri)
                    restored++
                }
                playlistRepository.hasPlaylistFor(uri) -> Unit
                playlistRepository.canRead(uri) -> {
                    playlistRepository.importPlaylist(playlist, uri)
                    restored++
                }
                else -> repick += playlist
            }
        }
        settle(backup)
        _foldersToRepick.value = _foldersToRepick.value + repick
        _restoreOffer.value = null
        toast(
            when {
                repick.isEmpty() -> "Settings and playlists restored"
                else -> "Settings restored. Pick the folder for each of the other ${repick.size} playlists."
            },
        )
    }

    /** Turns the offer down: the folder stays as just added, and this install's backup takes over. */
    fun declineRestore() {
        _restoreOffer.value?.let { settle(it.backup) }
        _restoreOffer.value = null
    }

    fun setBackupToFolders(value: Boolean) = launchSafely { settingsRepository.setBackupToFolders(value) }

    /** Whether the upcoming songs list is open; null until it's been read, so it doesn't flash shut. */
    val upcomingExpanded: StateFlow<Boolean?> = settingsRepository.upcomingExpanded
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setUpcomingExpanded(value: Boolean) = launchSafely { settingsRepository.setUpcomingExpanded(value) }

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
    fun playPlaylistShuffled(songs: List<Song>, label: String) = launchSafely {
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

    fun setShuffleSeedText(seed: String) = launchSafely {
        settingsRepository.setShuffleSeed(seed)
    }

    fun refreshShuffleSeed() = launchSafely {
        val newSeed = settingsRepository.regenerateShuffleSeed()
        playbackEngine.refreshShuffleSeed(newSeed)
    }

    fun setShuffleSeedLocked(locked: Boolean) = launchSafely {
        settingsRepository.setShuffleSeedLocked(locked)
    }

    // --- Settings toggles ----------------------------------------------------------------

    fun setNotificationMediaControl(value: Boolean) = launchSafely { settingsRepository.setNotificationMediaControl(value) }
    fun setRequireAuthToOpen(value: Boolean) = launchSafely { settingsRepository.setRequireAuthToOpen(value) }
    fun setRequireAuthToDelete(value: Boolean) = launchSafely { settingsRepository.setRequireAuthToDelete(value) }
    fun setTwoFingerBrightnessGesture(value: Boolean) = launchSafely { settingsRepository.setTwoFingerBrightnessGesture(value) }
    fun setThreeFingerVolumeGesture(value: Boolean) = launchSafely { settingsRepository.setThreeFingerVolumeGesture(value) }
    fun setGlassTheming(value: Boolean) = launchSafely { settingsRepository.setGlassTheming(value) }
    fun setTwentyFourHourClock(value: Boolean) = launchSafely { settingsRepository.setTwentyFourHourClock(value) }
    fun setCrossfadeMs(value: Int) = launchSafely { settingsRepository.setCrossfadeMs(value) }
    fun setBackgroundPlayback(value: Boolean) = launchSafely { settingsRepository.setBackgroundPlayback(value) }
    fun setBackgroundColorHex(value: String) = launchSafely { settingsRepository.setBackgroundColorHex(value) }
    fun setHideIconBackgrounds(value: Boolean) = launchSafely { settingsRepository.setHideIconBackgrounds(value) }
    fun setScrollLongTitles(value: Boolean) = launchSafely { settingsRepository.setScrollLongTitles(value) }
    fun setRestartSongOnOpen(value: Boolean) = launchSafely { settingsRepository.setRestartSongOnOpen(value) }

    fun setDailyRandomBackground(value: Boolean) = launchSafely {
        settingsRepository.setDailyRandomBackground(value)
        // Turning it on takes effect immediately rather than at the next midnight.
        if (value) settingsRepository.applyDailyRandomColorIfDue(todayStamp())
    }

    private fun todayStamp(): String =
        java.time.LocalDate.now().toString()

    /** Called when the app leaves the foreground; stops the music if background playback is off. */
    fun onAppBackgrounded() {
        appVisible.value = false
        playbackEngine.uiVisible = false
        if (!settingsSnapshot.value.backgroundPlayback) playbackEngine.pause()
        playbackEngine.persistNow()
    }

    // --- Effects ---------------------------------------------------------------------------

    fun setGlobalEffectsEnabled(enabled: Boolean) = updateEffects { it.copy(globalEnabled = enabled) }

    fun updateSpeed(transform: (SpeedEffectState) -> SpeedEffectState) = updateEffects { it.copy(speed = transform(it.speed)) }
    fun updatePitch(transform: (PitchEffectState) -> PitchEffectState) = updateEffects { it.copy(pitch = transform(it.pitch)) }
    fun updateReverb(transform: (ReverbEffectState) -> ReverbEffectState) = updateEffects { it.copy(reverb = transform(it.reverb)) }
    fun updateThreeD(transform: (ThreeDEffectState) -> ThreeDEffectState) = updateEffects { it.copy(threeD = transform(it.threeD)) }

    /** Puts one effect's settings back to their defaults, leaving it switched on or off as it was. */
    fun resetSpeed() = updateSpeed { SpeedEffectState(enabled = it.enabled) }
    fun resetPitch() = updatePitch { PitchEffectState(enabled = it.enabled) }
    fun resetReverb() = updateReverb { ReverbEffectState(enabled = it.enabled) }
    fun resetThreeD() = updateThreeD { ThreeDEffectState(enabled = it.enabled) }

    fun applyReverbPreset(preset: ReverbPreset) = updateEffects {
        it.copy(reverb = ReverbEffectState.fromPreset(preset, enabled = it.reverb.enabled))
    }

    private fun updateEffects(transform: (EffectsState) -> EffectsState) = launchSafely {
        effectsRepository.update { old ->
            val new = transform(old)
            // Switching an effect on with "All effects" off did nothing audible, with no hint as
            // to why. Turning one on now switches the master on too.
            val turnedOn = (!old.speed.enabled && new.speed.enabled) || (!old.pitch.enabled && new.pitch.enabled) ||
                (!old.reverb.enabled && new.reverb.enabled) || (!old.threeD.enabled && new.threeD.enabled)
            if (turnedOn && !new.globalEnabled) new.copy(globalEnabled = true) else new
        }
    }

    // --- Sleep timer -------------------------------------------------------------------------

    fun sleepTimerPrefill(): Pair<Int, Int> = playbackEngine.sleepTimerManager.remainingHoursMinutesForPrefill()

    fun confirmSleepTimer(hours: Int, minutes: Int, playLastSongToEnd: Boolean) {
        playbackEngine.sleepTimerManager.confirm(hours, minutes, playLastSongToEnd)
    }

    fun cancelSleepTimer() = playbackEngine.sleepTimerManager.cancel()
}

/** A backup from an earlier install, found in [folderUri] when it was added as [playlistName]. */
data class RestoreOffer(val backup: LibraryExport, val folderUri: String, val playlistName: String)

/** The songs of the playlist being viewed, and which playlist that is - see [MainViewModel.viewedSongs]. */
data class ViewedSongs(val playlistId: Long?, val songs: List<Song>)

/** What the playlist list shows, once both parts have been read. */
data class LibraryOverview(val playlists: List<Playlist>, val favourites: List<Song>)

/** Library-wide search results and the (trimmed) query they answer. */
data class SearchResults(val query: String, val songs: List<Song>)

/** Stands in for the settings until they've been read. */
private val DEFAULT_SETTINGS = SettingsSnapshot()

/** The longest the player shows no song while the saved queue is being put back. */
private const val RESTORE_PLACEHOLDER_LIMIT_MS = 2_000L
