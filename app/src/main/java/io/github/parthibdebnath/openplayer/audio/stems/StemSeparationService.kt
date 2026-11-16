package io.github.parthibdebnath.openplayer.audio.stems

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs stem separation in a process of its own (":stems" in the manifest).
 *
 * The network needs over 2 GB of memory while it runs. In the player's own process, a phone
 * running short of memory - a game opened, say - could have Android kill the music to get it
 * back. Out here, bound with [android.content.Context.BIND_WAIVE_PRIORITY], this process is what
 * Android kills first: the music plays on, and the song just gets a mix without stems. The
 * process ends itself once the app has no more work for it, so the memory is given back at once.
 *
 * One job at a time, asked for and answered by [Messenger] (see [StemSeparationClient]).
 */
class StemSeparationService : Service() {
    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private val messenger = Messenger(Handler(Looper.getMainLooper(), ::handle))

    /** The job being worked on, by key, so it can be cancelled. */
    private val current = AtomicReference<String?>(null)
    @Volatile private var cancelled: String? = null

    private var core: OnnxDemucsCore? = null
    private var separator: StemSeparator? = null

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("stem-separation", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
        worker = Handler(workerThread.looper)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        // No more work: free the network's memory by ending the process, once the worker is done.
        worker.post {
            runCatching { core?.close() }
            Process.killProcess(Process.myPid())
        }
        return false
    }

    private fun handle(message: Message): Boolean {
        when (message.what) {
            MSG_SEPARATE -> {
                val data = message.data
                val replyTo = message.replyTo ?: return true
                val key = data.getString(KEY_KEY) ?: return true
                worker.post { separate(key, data, replyTo) }
            }
            MSG_CANCEL -> cancelled = message.data.getString(KEY_KEY)
        }
        return true
    }

    private fun separate(key: String, data: Bundle, replyTo: Messenger) {
        current.set(key)
        if (cancelled == key) cancelled = null
        val outcome = try {
            val separator = separator ?: run {
                val c = core ?: StemJob.loadCore(this).also { core = it }
                StemSeparator(c).also { separator = it }
            }
            val file = File(data.getString(KEY_PATH)!!)
            val speed = StemJob.run(
                context = this,
                separator = separator,
                uri = Uri.parse(data.getString(KEY_URI)!!),
                region = StemRegion.entries[data.getInt(KEY_REGION)],
                fromMs = data.getDouble(KEY_FROM),
                toMs = data.getDouble(KEY_TO),
                file = file,
                shouldStop = { cancelled == key },
                onSegment = { done, total -> reply(replyTo, MSG_PROGRESS, key, done, total) },
            )
            when {
                speed != null -> Outcome(RESULT_DONE, speed)
                cancelled == key -> Outcome(RESULT_CANCELLED, 0.0)
                else -> Outcome(RESULT_FAILED, 0.0)
            }
        } catch (t: Throwable) {
            // OutOfMemoryError included: no stems for this one, and the process carries on.
            Log.e(TAG, "Separation failed", t)
            Outcome(RESULT_FAILED, 0.0)
        }
        current.set(null)
        reply(replyTo, MSG_RESULT, key, outcome.code, 0, outcome.speed)
    }

    private class Outcome(val code: Int, val speed: Double)

    private fun reply(to: Messenger, what: Int, key: String, arg1: Int, arg2: Int, speed: Double = 0.0) {
        val message = Message.obtain(null, what, arg1, arg2)
        message.data = Bundle().apply {
            putString(KEY_KEY, key)
            putDouble(KEY_SPEED, speed)
        }
        runCatching { to.send(message) }
    }

    override fun onDestroy() {
        workerThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StemService"

        const val MSG_SEPARATE = 1
        const val MSG_CANCEL = 2
        const val MSG_PROGRESS = 3
        const val MSG_RESULT = 4

        const val RESULT_DONE = 0
        const val RESULT_FAILED = 1
        const val RESULT_CANCELLED = 2

        const val KEY_KEY = "key"
        const val KEY_URI = "uri"
        const val KEY_REGION = "region"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        const val KEY_PATH = "path"
        const val KEY_SPEED = "speed"
    }
}
