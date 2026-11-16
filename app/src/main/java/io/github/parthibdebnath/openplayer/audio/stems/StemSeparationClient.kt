package io.github.parthibdebnath.openplayer.audio.stems

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The player's side of [StemSeparationService]: binds to it while there's work, hands it one job
 * at a time and waits for the answer. Main thread only.
 */
internal class StemSeparationClient(context: Context) {
    private val appContext = context.applicationContext

    sealed interface Outcome {
        data class Done(val speed: Double) : Outcome
        data object Failed : Outcome
        data object Cancelled : Outcome

        /** The separation process went away mid-job - most likely taken back for its memory. */
        data object Killed : Outcome
    }

    private var service: Messenger? = null
    private var bound = false
    private val connectWaiters = ArrayList<CancellableContinuation<Messenger?>>()

    private class Pending(val key: String, val continuation: CancellableContinuation<Outcome>, val onProgress: (Int, Int) -> Unit)
    private var pending: Pending? = null

    private val replies = Messenger(Handler(Looper.getMainLooper()) { message -> onReply(message); true })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val messenger = binder?.let(::Messenger)
            service = messenger
            connectWaiters.toList().forEach { if (it.isActive) it.resume(messenger) }
            connectWaiters.clear()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            finish(Outcome.Killed)
        }

        override fun onBindingDied(name: ComponentName?) {
            service = null
            finish(Outcome.Killed)
            release()
        }
    }

    private suspend fun connect(): Messenger? {
        service?.let { return it }
        if (!bound) {
            // Waived priority: the separation process doesn't share the player's standing with
            // the system, so it's the one let go when memory runs short.
            bound = runCatching {
                appContext.bindService(
                    Intent(appContext, StemSeparationService::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE or Context.BIND_WAIVE_PRIORITY or Context.BIND_NOT_FOREGROUND,
                )
            }.getOrDefault(false)
            if (!bound) return null
        }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                connectWaiters += continuation
                continuation.invokeOnCancellation { connectWaiters.remove(continuation) }
            }
        }
    }

    suspend fun separate(
        key: String,
        uri: String,
        region: StemRegion,
        fromMs: Double,
        toMs: Double,
        path: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Outcome {
        val messenger = connect() ?: return Outcome.Killed
        return suspendCancellableCoroutine { continuation ->
            pending = Pending(key, continuation, onProgress)
            val message = Message.obtain(null, StemSeparationService.MSG_SEPARATE)
            message.replyTo = replies
            message.data = Bundle().apply {
                putString(StemSeparationService.KEY_KEY, key)
                putString(StemSeparationService.KEY_URI, uri)
                putInt(StemSeparationService.KEY_REGION, region.ordinal)
                putDouble(StemSeparationService.KEY_FROM, fromMs)
                putDouble(StemSeparationService.KEY_TO, toMs)
                putString(StemSeparationService.KEY_PATH, path)
            }
            if (runCatching { messenger.send(message) }.isFailure) finish(Outcome.Killed)
            continuation.invokeOnCancellation { cancel(key) }
        }
    }

    /** Asks the service to stop [key] at its next segment. */
    fun cancel(key: String) {
        val messenger = service ?: return
        val message = Message.obtain(null, StemSeparationService.MSG_CANCEL)
        message.data = Bundle().apply { putString(StemSeparationService.KEY_KEY, key) }
        runCatching { messenger.send(message) }
    }

    /** No more work: unbinds, and the separation process ends, giving its memory back. */
    fun release() {
        if (bound) runCatching { appContext.unbindService(connection) }
        bound = false
        service = null
    }

    private fun onReply(message: Message) {
        val key = message.data?.getString(StemSeparationService.KEY_KEY)
        val job = pending ?: return
        if (key != job.key) return
        when (message.what) {
            StemSeparationService.MSG_PROGRESS -> job.onProgress(message.arg1, message.arg2)
            StemSeparationService.MSG_RESULT -> finish(
                when (message.arg1) {
                    StemSeparationService.RESULT_DONE -> Outcome.Done(message.data.getDouble(StemSeparationService.KEY_SPEED))
                    StemSeparationService.RESULT_CANCELLED -> Outcome.Cancelled
                    else -> Outcome.Failed
                },
            )
        }
    }

    private fun finish(outcome: Outcome) {
        val job = pending ?: return
        pending = null
        if (outcome is Outcome.Killed) Log.w("Stems", "Separation process went away during ${job.key}")
        if (job.continuation.isActive) job.continuation.resume(outcome)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
