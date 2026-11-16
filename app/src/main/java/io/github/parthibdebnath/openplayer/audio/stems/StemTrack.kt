package io.github.parthibdebnath.openplayer.audio.stems

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import kotlin.math.log10
import kotlin.math.max

/** Which stretch of a song a set of stems covers. */
enum class StemRegion { HEAD, TAIL, WHOLE }

/**
 * One stretch of a song separated into stems, read from its file in the stem cache.
 *
 * Every frame holds the song itself (as decoded, for lining the stems up with what the player is
 * playing) and its drums, bass and vocals, interleaved, 16-bit stereo - so the audio thread reads
 * one run of memory per frame. The file is memory-mapped: nothing is copied onto the heap, and
 * the system keeps it in its page cache while it's being played.
 *
 * Also holds how loud each stem is every [BLOCK_MS], which the mix planner uses to see where
 * vocals come and go, where the drums drop out, and so on.
 */
class StemTrack private constructor(
    val region: StemRegion,
    val sampleRate: Int,
    /** Channels the song itself has (the data is always stereo). */
    val sourceChannels: Int,
    /** Media time of frame 0. */
    val startMs: Double,
    val frames: Int,
    private val activity: Array<FloatArray>,
    internal val data: ShortBuffer,
    private val mapped: MappedByteBuffer,
) {
    val endMs: Double get() = startMs + frames * 1000.0 / sampleRate

    /** Whether [fromMs, toMs] lies within this track. */
    fun covers(fromMs: Double, toMs: Double): Boolean = fromMs >= startMs && toMs <= endMs

    /**
     * Mean loudness of [stem] over [fromMs, toMs] in dBFS (power average), or null outside the
     * track.
     */
    fun levelDb(stem: Stem, fromMs: Double, toMs: Double): Double? {
        val blocks = activity[stem.ordinal]
        if (blocks.isEmpty()) return null
        val first = ((fromMs - startMs) / BLOCK_MS).toInt()
        val last = ((toMs - startMs) / BLOCK_MS).toInt()
        if (last < 0 || first >= blocks.size) return null
        var power = 0.0
        var count = 0
        for (b in first.coerceAtLeast(0)..last.coerceAtMost(blocks.size - 1)) {
            power += dbToPower(blocks[b])
            count++
        }
        return if (count == 0) null else powerToDb(power / count)
    }

    /** Reads the whole file into memory now, off the audio thread, so playing it never waits on storage. */
    fun preload() {
        runCatching { mapped.load() }
    }

    enum class Stem { MIX, DRUMS, BASS, VOCALS }

    companion object {
        const val BLOCK_MS = 50.0

        /** Shorts per frame: mix, drums, bass, vocals, each left and right. */
        internal const val FRAME_SHORTS = 8

        private const val MAGIC = 0x4F505354454D3031L // "OPSTEM01"
        private const val VERSION = 1
        private const val SILENCE_DB = -100f

        /** Opens a stem file, or null if it isn't one (or is damaged). */
        fun open(file: File): StemTrack? = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val channel = raf.channel
                val headerFixed = ByteBuffer.allocate(HEADER_FIXED).order(ByteOrder.LITTLE_ENDIAN)
                channel.read(headerFixed, 0)
                headerFixed.flip()
                if (headerFixed.getLong() != MAGIC || headerFixed.getInt() != VERSION) return null
                val region = StemRegion.entries[headerFixed.getInt()]
                val rate = headerFixed.getInt()
                val channels = headerFixed.getInt()
                val startMs = headerFixed.getDouble()
                val frames = headerFixed.getInt()
                val blocks = headerFixed.getInt()
                val activityBytes = 4 * blocks * 4
                val activityBuffer = ByteBuffer.allocate(activityBytes).order(ByteOrder.LITTLE_ENDIAN)
                channel.read(activityBuffer, HEADER_FIXED.toLong())
                activityBuffer.flip()
                val activity = Array(4) { FloatArray(blocks) { activityBuffer.getFloat() } }
                val dataStart = dataOffset(blocks)
                val dataBytes = frames.toLong() * FRAME_SHORTS * 2
                if (channel.size() < dataStart + dataBytes) return null
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, dataStart, dataBytes)
                mapped.order(ByteOrder.LITTLE_ENDIAN)
                StemTrack(region, rate, channels, startMs, frames, activity, mapped.asShortBuffer(), mapped)
            }
        }.getOrNull()

        /**
         * Writes a stem file: [mix] as decoded, and the three stems, frame for frame with it -
         * clipped to 16 bits. Written to a temporary file and moved
         * into place, so a half-written file is never opened.
         */
        internal fun write(
            file: File,
            region: StemRegion,
            mix: DecodedWindow,
            /** Drums, bass and vocals, each (left, right) at the song's own rate. */
            stems: List<Pair<SampleReader, SampleReader>>,
        ) {
            val frames = mix.frames
            val rate = mix.sampleRate
            val blockFrames = max(1, (rate * BLOCK_MS / 1000).toInt())
            val blocks = (frames + blockFrames - 1) / blockFrames
            val activity = Array(4) { FloatArray(blocks) }
            require(stems.size == 3)

            val temp = File(file.parentFile, file.name + ".part")
            RandomAccessFile(temp, "rw").use { raf ->
                raf.setLength(0)
                val channel = raf.channel
                val dataStart = dataOffset(blocks)
                val chunkFrames = 16_384
                val chunk = ByteBuffer.allocate(chunkFrames * FRAME_SHORTS * 2).order(ByteOrder.LITTLE_ENDIAN)
                val power = DoubleArray(4)
                var frameInBlock = 0
                var block = 0
                var position = dataStart
                for (f in 0 until frames) {
                    val ml = mix.data.get(2 * f)
                    val mr = mix.data.get(2 * f + 1)
                    chunk.putShort(ml)
                    chunk.putShort(mr)
                    val m = (ml + mr) / 65_536.0
                    power[0] += m * m
                    for ((s, stem) in stems.withIndex()) {
                        val l = stem.first[f]
                        val r = stem.second[f]
                        chunk.putShort(toShort(l))
                        chunk.putShort(toShort(r))
                        val v = (l + r) * 0.5
                        power[s + 1] += v * v
                    }
                    frameInBlock++
                    if (frameInBlock == blockFrames || f == frames - 1) {
                        for (s in 0 until 4) activity[s][block] = powerToDb(power[s] / frameInBlock).toFloat()
                        power.fill(0.0)
                        frameInBlock = 0
                        block++
                    }
                    if (!chunk.hasRemaining()) {
                        chunk.flip()
                        while (chunk.hasRemaining()) position += channel.write(chunk, position)
                        chunk.clear()
                    }
                }
                chunk.flip()
                while (chunk.hasRemaining()) position += channel.write(chunk, position)

                val header = ByteBuffer.allocate(dataStart.toInt()).order(ByteOrder.LITTLE_ENDIAN)
                header.putLong(MAGIC)
                header.putInt(VERSION)
                header.putInt(region.ordinal)
                header.putInt(rate)
                header.putInt(mix.channels)
                header.putDouble(mix.startMs)
                header.putInt(frames)
                header.putInt(blocks)
                for (s in 0 until 4) for (b in 0 until blocks) header.putFloat(activity[s][b])
                header.position(0)
                var at = 0L
                while (header.hasRemaining()) at += channel.write(header, at)
                channel.force(false)
            }
            if (!temp.renameTo(file)) {
                temp.delete()
                error("Could not move $temp into place")
            }
        }

        private const val HEADER_FIXED = 8 + 4 + 4 + 4 + 4 + 8 + 4 + 4

        /** Data starts on a 64-byte boundary after the header and activity. */
        private fun dataOffset(blocks: Int): Long {
            val end = HEADER_FIXED + 4L * blocks * 4
            return (end + 63) / 64 * 64
        }

        private fun toShort(v: Float): Short = (v * 32768f).toInt().coerceIn(-32768, 32767).toShort()

        private fun dbToPower(db: Float): Double = Math.pow(10.0, db / 10.0)

        private fun powerToDb(power: Double): Double =
            if (power <= 1e-10) SILENCE_DB.toDouble() else 10 * log10(power)
    }
}
