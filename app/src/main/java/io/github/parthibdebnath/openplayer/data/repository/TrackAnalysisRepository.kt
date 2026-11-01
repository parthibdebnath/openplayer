package io.github.parthibdebnath.openplayer.data.repository

import android.content.Context
import android.net.Uri
import android.os.Process
import android.util.Log
import io.github.parthibdebnath.openplayer.audio.analysis.AudioDecoder
import io.github.parthibdebnath.openplayer.audio.analysis.BeatAnalyzer
import io.github.parthibdebnath.openplayer.audio.analysis.TrackAnalysis
import io.github.parthibdebnath.openplayer.data.db.AppDatabase
import io.github.parthibdebnath.openplayer.data.db.TrackAnalysisEntity
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.util.runSuspendCatching
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The DJ mix's song analyses: read from the database when a song has been analysed before,
 * worked out in the background when it hasn't.
 *
 * Main thread only, apart from the work itself. [analysisFor] answers at once from memory, so the
 * playback engine can ask on every tick; [prefetch] says which songs are about to be needed, most
 * urgent first, and they're analysed one at a time on a low-priority thread, newest request
 * first. [onReady] is told as each one becomes available.
 */
class TrackAnalysisRepository(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val dao by lazy { AppDatabase.get(appContext).trackAnalysisDao() }

    /**
     * Analysis can take a second or two of CPU for a long song; on its own background thread it
     * never competes with playback or the UI.
     */
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "track-analysis")
    }.asCoroutineDispatcher()

    private class Entry(val sizeBytes: Long, val lastModified: Long, val analysis: TrackAnalysis?)

    /** Recently used analyses (null for songs that couldn't be analysed), by song id. */
    private val memory = object : LinkedHashMap<Long, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>?) = size > MEMORY_SIZE
    }

    private val queue = ArrayDeque<Song>()
    private var running: Job? = null

    var onReady: ((songId: Long) -> Unit)? = null

    /** The analysis of [song] if it's to hand, or null - not analysed yet, or not analysable. */
    fun analysisFor(song: Song): TrackAnalysis? = memory[song.id]?.takeIf { it.matches(song) }?.analysis

    /** Whether [song] has been looked at, analysable or not. */
    fun isSettled(song: Song): Boolean = memory[song.id]?.matches(song) == true

    /** Makes sure [songs] get analysed, most urgent first, ahead of anything asked for before. */
    fun prefetch(songs: List<Song>) {
        val wanted = songs.filter { !isSettled(it) }.distinctBy { it.id }
        if (wanted.isEmpty()) return
        val ids = wanted.mapTo(HashSet()) { it.id }
        queue.removeAll { it.id in ids }
        wanted.asReversed().forEach { queue.addFirst(it) }
        // Older requests beyond a handful are dropped: they were for songs that may never come.
        while (queue.size > MAX_QUEUE) queue.removeLast()
        if (running?.isActive != true) running = scope.launch { drain() }
    }

    private suspend fun drain() {
        while (true) {
            val song = queue.removeFirstOrNull() ?: return
            if (isSettled(song)) continue
            val entry = load(song)
            memory[song.id] = entry
            onReady?.invoke(song.id)
        }
    }

    private suspend fun load(song: Song): Entry {
        val stored = if (song.isExternal) null else runSuspendCatching {
            withContext(Dispatchers.IO) { dao.get(song.id) }
        }.getOrNull()
        if (stored != null && stored.version == TrackAnalysis.VERSION &&
            stored.sizeBytes == song.sizeBytes && stored.lastModified == song.lastModified
        ) {
            return Entry(song.sizeBytes, song.lastModified, stored.toAnalysis())
        }
        val analysis = withContext(worker) {
            val started = System.nanoTime()
            val decoded = AudioDecoder.decode(appContext, Uri.parse(song.documentUri))
            // A fault in the analysis just means no DJ mix into or out of this song; it must never
            // reach the engine's scope, where it would take the app down.
            val result = decoded?.let { audio ->
                runCatching { BeatAnalyzer.analyze(audio.samples, audio.sampleRate, audio.startMs) }
                    .onFailure { Log.e("TrackAnalysis", "Analysis failed for ${song.displayName}", it) }
                    .getOrNull()
            }
            Log.i(
                "TrackAnalysis",
                "${song.displayName}: ${result?.let { "%.2f BPM (conf %.2f, stable %b), key %d".format(it.bpm, it.beatConfidence, it.tempoStable, it.key) } ?: "no analysis"} " +
                    "in ${(System.nanoTime() - started) / 1_000_000} ms",
            )
            result
        }
        // Only a result is kept. A failure stays in memory for this run only: a decoder that gave
        // up on a busy phone, or a file still being copied, deserves another try next time.
        if (!song.isExternal && analysis != null) {
            runSuspendCatching {
                withContext(Dispatchers.IO) {
                    dao.put(TrackAnalysisEntity.of(song.id, song.sizeBytes, song.lastModified, analysis))
                }
            }
        }
        return Entry(song.sizeBytes, song.lastModified, analysis)
    }

    private fun Entry.matches(song: Song) = sizeBytes == song.sizeBytes && lastModified == song.lastModified

    private companion object {
        const val MEMORY_SIZE = 64
        const val MAX_QUEUE = 8
    }
}
