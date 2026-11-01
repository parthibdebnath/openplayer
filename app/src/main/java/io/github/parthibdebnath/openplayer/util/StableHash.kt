package io.github.parthibdebnath.openplayer.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * A 64-bit FNV-1a hash of [text]'s UTF-8 bytes. Fixed by its definition rather than by a library,
 * so it gives the same number on every device, Android version and release - unlike
 * String.hashCode, which is only 32 bits and collides easily.
 */
fun stableHash64(text: String): Long {
    var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325, the FNV-1a 64-bit offset basis
    for (byte in text.encodeToByteArray()) {
        hash = (hash xor (byte.toLong() and 0xFF)) * 0x100000001b3L
    }
    return hash
}

/**
 * runCatching for suspend code. The plain one also catches [CancellationException], so a
 * coroutine cancelled while inside it carried on as if a call had merely failed - running the
 * rest of a job that had been called off.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    Result.failure(t)
}
