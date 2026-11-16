package io.github.parthibdebnath.openplayer.audio.stems

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/**
 * Demucs v4's network on ONNX Runtime's CPU provider, in full 32-bit float: the separation is only
 * worth doing at its best quality, and the phone has minutes to do it in, so neither a quantised
 * model nor the NNAPI/GPU providers (which run in 16-bit floats on most phones) are used.
 *
 * The session's worker threads are started by the thread that creates it, and inherit that
 * thread's (background) priority, so playback and the UI always come first.
 *
 * [model] is the ONNX file, in a direct buffer (mapped straight from the APK).
 */
internal class OnnxDemucsCore(model: ByteBuffer, threads: Int) : DemucsCore {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val mixName: String
    private val specName: String
    private val freqName: String
    private val timeName: String

    init {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setInterOpNumThreads(1)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // Spinning threads would keep cores busy between the model's layers for nothing.
            addConfigEntry("session.intra_op.allow_spinning", "0")
            // Memory over a little speed: the arena and the pre-planned memory pattern hold on to
            // every buffer's peak size at once, which on Android took the process past 4.5 GB.
            // Without them each buffer is freed as soon as it's used up.
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
        }
        session = options.use { env.createSession(model, it) }
        val inputs = session.inputNames.toList()
        val outputs = session.outputNames.toList()
        require(inputs.size == 2 && outputs.size == 2) { "Unexpected model signature: $inputs -> $outputs" }
        // As exported: (waveform, spectrogram) -> (frequency branch, time branch).
        mixName = inputs[0]
        specName = inputs[1]
        freqName = outputs[0]
        timeName = outputs[1]
    }

    override fun run(mix: FloatBuffer, spec: FloatBuffer, freqOut: FloatBuffer, timeOut: FloatBuffer) {
        mix.clear(); spec.clear(); freqOut.clear(); timeOut.clear()
        OnnxTensor.createTensor(env, mix, MIX_SHAPE).use { mixTensor ->
            OnnxTensor.createTensor(env, spec, SPEC_SHAPE).use { specTensor ->
                // Written straight into the caller's buffers: the outputs are 55 MB a segment,
                // too much to copy out every time.
                OnnxTensor.createTensor(env, freqOut, FREQ_SHAPE).use { freqTensor ->
                    OnnxTensor.createTensor(env, timeOut, TIME_SHAPE).use { timeTensor ->
                        session.run(
                            mapOf(mixName to mixTensor, specName to specTensor),
                            mapOf(freqName to freqTensor, timeName to timeTensor),
                        ).close()
                    }
                }
            }
        }
    }

    override fun close() {
        session.close()
    }

    private companion object {
        val MIX_SHAPE = longArrayOf(1, 2, Demucs.SEGMENT.toLong())
        val SPEC_SHAPE = longArrayOf(1, 4, Demucs.BINS.toLong(), Demucs.FRAMES.toLong())
        val FREQ_SHAPE = longArrayOf(1, Demucs.SOURCES.toLong(), 4, Demucs.BINS.toLong(), Demucs.FRAMES.toLong())
        val TIME_SHAPE = longArrayOf(1, Demucs.SOURCES.toLong(), 2, Demucs.SEGMENT.toLong())
    }
}
