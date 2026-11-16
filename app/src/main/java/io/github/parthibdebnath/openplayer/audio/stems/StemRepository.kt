package io.github.parthibdebnath.openplayer.audio.stems

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import io.github.parthibdebnath.openplayer.audio.analysis.TrackAnalysis
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.data.repository.isExternal
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** What the stem separation is up to, for Settings. */
data class StemStatus(
    val supported: Boolean,
    /** The song being separated now, if any. */
    val working: String? = null,
    val songsReady: Int = 0,
    /** Seconds of audio separated per second taken, over the last separation; 0 before any. */
    val lastSpeed: Double = 0.0,
    /** Why it's waiting, if it is: battery saver, the phone too hot. */
    val pausedFor: String? = null,
)

/**
 * The DJ mix's stems: the start and end of each song coming up, separated into drums, bass,
 * vocals and the rest by Demucs v4 on the phone, kept in a cache on disk (about 80 MB a song) so a
 * song is only ever separated once.
 *
 * Main thread only, apart from the work. The engine says which stretches it wants, most urgent
 * first ([want]) - the end of the song playing and the start of the next, then the next one's end
 * and the start of the one after - and asks [tracksFor] whenever it needs them; [onReady] is told
 * as each is done. Separation takes minutes of CPU, in a background-priority process of its own
 * ([StemSeparationService]), and stops between segments when what it's doing is no longer wanted. Nothing ever waits for it: a
 * mix whose stems aren't ready is mixed without them.
 */
class StemRepository(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "stems")

    /** Whether this phone can separate at all: a 64-bit CPU, plenty of memory, the model present. */
    val supported: Boolean by lazy { deviceSupported() && modelPresent() }

    /** The "Separate stems" setting. */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) wanted = emptyList()
        }

    var onReady: ((songId: Long) -> Unit)? = null

    private val _status = MutableStateFlow(StemStatus(supported = false))
    val status: StateFlow<StemStatus> = _status.asStateFlow()

    /** A stretch of one song. */
    data class Request(val song: Song, val region: StemRegion, val fromMs: Double, val toMs: Double) {
        val key: String get() = "${song.id}_${song.sizeBytes}_${song.lastModified}_${region.name.lowercase()}"
    }

    @Volatile private var wanted: List<Request> = emptyList()

    private val client = StemSeparationClient(appContext)

    /** Times each stretch's separation process was killed under it this run. */
    private val killed = HashMap<String, Int>()
    private var running: Job? = null

    /** Opened tracks, by request key. */
    private val open = object : LinkedHashMap<String, StemTrack>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StemTrack>?) = size > OPEN_TRACKS
    }

    /** Keys known to have failed this run (undecodable, unmatchable), not retried. */
    private val failed = HashSet<String>()

    init {
        scope.launch {
            val ready = withContext(Dispatchers.IO) { cachedSongCount() }
            _status.value = StemStatus(supported = supported, songsReady = ready)
        }
    }

    /** The stretches [song] needs separating into, from its analysis. */
    fun requestsFor(song: Song, analysis: TrackAnalysis): List<Request> {
        val duration = analysis.durationMs
        if (song.isExternal || duration <= 0) return emptyList()
        val headEnd = min(duration, analysis.audioStartMs + HEAD_MS)
        val tailStart = max(0.0, analysis.audioEndMs - TAIL_MS)
        return if (tailStart <= headEnd + MERGE_GAP_MS) {
            listOf(Request(song, StemRegion.WHOLE, 0.0, duration))
        } else {
            listOf(
                Request(song, StemRegion.HEAD, 0.0, headEnd),
                Request(song, StemRegion.TAIL, tailStart, duration + END_SLACK_MS),
            )
        }
    }

    /** The stems to hand for [song], from any of its stretches. Never touches storage. */
    fun tracksFor(song: Song): List<StemTrack> {
        val prefix = "${song.id}_${song.sizeBytes}_${song.lastModified}_"
        return open.entries.filter { it.key.startsWith(prefix) }.map { it.value }
    }

    /**
     * Says which stretches are wanted, most urgent first. Anything not in the list stops being
     * worked on; anything in it not yet done is done, in order.
     */
    fun want(requests: List<Request>) {
        if (!enabled || !supported) return
        val list = requests.filter { it.key !in open && it.key !in failed }.distinctBy { it.key }
        if (list.map { it.key } == wanted.map { it.key } && running?.isActive == true) return
        wanted = list
        if (list.isNotEmpty() && running?.isActive != true) running = scope.launch { drain() }
    }

    private fun stillWanted(key: String) = enabled && wanted.any { it.key == key }

    private suspend fun drain() {
        try {
            while (true) {
                val request = wanted.firstOrNull { it.key !in open && it.key !in failed } ?: return
                val pause = pauseReason()
                if (pause != null) {
                    _status.value = _status.value.copy(working = null, pausedFor = pause)
                    // Looked at again in a while; the engine keeps asking in the meantime.
                    delay(PAUSE_RECHECK_MS)
                    continue
                }
                val file = File(cacheDir, request.key + ".stem")
                val inUse = open.keys.map { "$it.stem" }.toSet()
                val existing = withContext(Dispatchers.IO) {
                    if (file.exists()) {
                        file.setLastModified(System.currentTimeMillis())
                        StemTrack.open(file)
                    } else {
                        null
                    }
                }
                val track = existing ?: separate(request, file, inUse)
                if (track != null) {
                    open[request.key] = track
                    onReady?.invoke(request.song.id)
                }
                _status.value = _status.value.copy(working = null, pausedFor = null)
            }
        } finally {
            // The separation process holds the whole network in memory; it ends once unbound.
            client.release()
        }
    }

    /** Has [request] separated in the separation process; the track, or null if it wasn't. */
    private suspend fun separate(request: Request, file: File, inUse: Set<String>): StemTrack? {
        val song = request.song
        if (!withContext(Dispatchers.IO) { ensureSpace(inUse) }) return null
        _status.value = _status.value.copy(working = song.displayName, pausedFor = null)
        val outcome = coroutineScope {
            // Stopped between segments once it's no longer wanted, or the phone needs a rest.
            val watcher = launch {
                while (true) {
                    delay(CANCEL_CHECK_MS)
                    if (!stillWanted(request.key) || pauseReason() != null) client.cancel(request.key)
                }
            }
            try {
                client.separate(request.key, song.documentUri, request.region, request.fromMs, request.toMs, file.path) { done, total ->
                    Log.d(TAG, "${song.displayName} ${request.region}: segment $done of $total")
                }
            } finally {
                watcher.cancel()
            }
        }
        when (outcome) {
            is StemSeparationClient.Outcome.Done -> {
                Log.i(TAG, "Separated ${song.displayName} ${request.region} at %.2fx real time".format(outcome.speed))
                val track = withContext(Dispatchers.IO) { StemTrack.open(file) }
                _status.value = _status.value.copy(
                    lastSpeed = outcome.speed,
                    songsReady = withContext(Dispatchers.IO) { cachedSongCount() },
                )
                if (track == null) failed.add(request.key)
                return track
            }
            StemSeparationClient.Outcome.Failed -> failed.add(request.key)
            StemSeparationClient.Outcome.Killed -> {
                // Taken back for its memory, most likely: tried again, but not forever.
                val kills = (killed[request.key] ?: 0) + 1
                killed[request.key] = kills
                if (kills >= MAX_KILLS) failed.add(request.key)
                client.release()
                delay(KILLED_BACKOFF_MS)
            }
            StemSeparationClient.Outcome.Cancelled -> Unit
        }
        withContext(Dispatchers.IO) { File(file.path + ".part").delete() }
        return null
    }

    private fun pauseReason(): String? {
        val power = appContext.getSystemService(PowerManager::class.java) ?: return null
        if (power.isPowerSaveMode) return "battery saver"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            return "phone too warm"
        }
        return null
    }

    private fun deviceSupported(): Boolean {
        if (!android.os.Process.is64Bit()) return false
        val activity = appContext.getSystemService(ActivityManager::class.java) ?: return false
        if (activity.isLowRamDevice) return false
        val info = ActivityManager.MemoryInfo()
        activity.getMemoryInfo(info)
        return info.totalMem >= MIN_TOTAL_MEMORY
    }

    private fun modelPresent(): Boolean =
        runCatching { appContext.assets.openFd(StemJob.MODEL_ASSET).use { it.length > 0 } }.getOrDefault(false)

    /** Keeps the cache under its size, oldest-used first; false if there's no room at all. */
    private fun ensureSpace(inUse: Set<String>): Boolean {
        cacheDir.mkdirs()
        val files = cacheDir.listFiles()?.filter { it.isFile }.orEmpty().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) break
            if (file.name in inUse) continue
            total -= file.length()
            file.delete()
        }
        return cacheDir.usableSpace > MIN_FREE_BYTES
    }

    private fun cachedSongCount(): Int =
        cacheDir.listFiles()?.filter { it.name.endsWith(".stem") }?.map { it.name.substringBefore('_') }?.toSet()?.size ?: 0

    private companion object {
        const val TAG = "Stems"

        /** How much of a song's start is separated: the longest mix in, with its lead-in. */
        const val HEAD_MS = 50_000.0

        /** How much of a song's end: the longest mix out, wherever in the outro it starts. */
        const val TAIL_MS = 60_000.0
        const val MERGE_GAP_MS = 20_000.0
        const val END_SLACK_MS = 1_000.0

        const val MIN_TOTAL_MEMORY = 5_500L * 1024 * 1024
        const val MAX_CACHE_BYTES = 1_500L * 1024 * 1024
        const val MIN_FREE_BYTES = 1_000L * 1024 * 1024
        const val OPEN_TRACKS = 8
        const val PAUSE_RECHECK_MS = 30_000L
        const val CANCEL_CHECK_MS = 2_000L
        const val MAX_KILLS = 2
        const val KILLED_BACKOFF_MS = 20_000L
    }
}
