package io.github.parthibdebnath.openplayer.audio.stems

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * One stretch of one song separated into a stem file: decoded, run through Demucs and written.
 * Runs in the separation process ([StemSeparationService]), on its worker thread.
 */
internal object StemJob {
    const val MODEL_ASSET = "stems/htdemucs.onnx"
    private const val TAG = "Stems"
    private const val MAX_THREADS = 4

    /** Seconds of audio separated per second taken, or null if it wasn't done. */
    fun run(
        context: Context,
        separator: StemSeparator,
        uri: Uri,
        region: StemRegion,
        fromMs: Double,
        toMs: Double,
        file: File,
        shouldStop: () -> Boolean,
        onSegment: (done: Int, total: Int) -> Unit,
    ): Double? {
        val started = SystemClock.elapsedRealtime()
        val window = StemDecoder.decode(context, uri, listOf(fromMs..toMs), shouldStop)?.firstOrNull() ?: return null
        if (window.frames < window.sampleRate) return null

        // To the model's 44.1 kHz, sample by sample as it needs them.
        val nativeLeft = InterleavedShortReader(window.data, 0, window.frames)
        val nativeRight = InterleavedShortReader(window.data, 1, window.frames)
        val toModel = Resampler(window.sampleRate, Demucs.SAMPLE_RATE)
        val modelLeft = if (toModel.identity) nativeLeft else ResampledReader(nativeLeft, toModel)
        val modelRight = if (toModel.identity) nativeRight else ResampledReader(nativeRight, toModel)

        val segments = separator.segmentCount(modelLeft.length)
        val result = separator.separate(modelLeft, modelRight, shouldStop) { done -> onSegment(done, segments) } ?: return null

        // Back to the song's own rate, frame for frame with what was decoded.
        val back = Resampler(Demucs.SAMPLE_RATE, window.sampleRate)
        fun native(data: ShortBuffer): Pair<SampleReader, SampleReader> {
            var l: SampleReader = InterleavedShortReader(data, 0, result.length)
            var r: SampleReader = InterleavedShortReader(data, 1, result.length)
            if (window.channels == 1) {
                // A mono song: the model saw it twice over; its stem is the average of the two.
                val mono = MonoReader(l, r)
                l = mono
                r = mono
            }
            if (back.identity) return l to r
            return ResampledReader(l, back) to ResampledReader(r, back)
        }
        StemTrack.write(file, region, window, listOf(native(result.drums), native(result.bass), native(result.vocals)))
        val seconds = (SystemClock.elapsedRealtime() - started) / 1000.0
        val audioSeconds = window.frames.toDouble() / window.sampleRate
        Log.i(TAG, "Separated $region: %.1f s of audio in %.1f s (%.2fx real time)".format(audioSeconds, seconds, audioSeconds / seconds))
        return audioSeconds / seconds
    }

    /**
     * The network, mapped straight out of the APK, for [fast] (performance mode) or not.
     *
     * Normally four threads. Performance mode uses one per fast core - the big and middle cores,
     * not the efficiency cores, whose slowness would hold every layer back (work is split evenly)
     * - and XNNPACK if [useXnnpack] says it's the faster here.
     */
    fun loadCore(context: Context, fast: Boolean, useXnnpack: Boolean = false): OnnxDemucsCore {
        val started = SystemClock.elapsedRealtime()
        val threads = if (fast) fastCoreCount() else min(MAX_THREADS, max(1, Runtime.getRuntime().availableProcessors() / 2))
        context.assets.openFd(MODEL_ASSET).use { fd: AssetFileDescriptor ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                return OnnxDemucsCore(buffer, threads, xnnpack = fast && useXnnpack).also {
                    Log.i(TAG, "Loaded the separation model in ${SystemClock.elapsedRealtime() - started} ms: $threads threads, fast $fast, XNNPACK ${it.xnnpack}")
                }
            }
        }
    }

    /**
     * Cores within 75% of the fastest one's top clock: on a phone, its big and middle cores. Half
     * the cores if the clocks can't be read.
     */
    private fun fastCoreCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        val clocks = (0 until cores).mapNotNull { cpu ->
            runCatching { File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
        }
        val top = clocks.maxOrNull()
        val fast = if (top == null || clocks.size < cores) cores / 2 else clocks.count { it >= top * 0.75 }
        return fast.coerceIn(1, MAX_FAST_THREADS)
    }

    private const val MAX_FAST_THREADS = 8
}

/** The average of two channels. */
internal class MonoReader(private val a: SampleReader, private val b: SampleReader) : SampleReader() {
    override val length: Int get() = a.length
    override fun get(index: Int): Float = (a[index] + b[index]) * 0.5f
}
