package io.github.parthibdebnath.openplayer.util

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * This [StateFlow] seen through [transform], worked out whenever it's read rather than collected
 * into a second StateFlow by a coroutine of its own.
 *
 * A second StateFlow catches up a dispatch later, so a screen could read the source as loaded and
 * the derived value as still the placeholder in the same frame - the very flash a "loaded" check
 * is there to prevent. Computed on read, the two always agree. [transform] runs on every read, so
 * it has to be trivial.
 */
fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> = MappedStateFlow(this, transform)

private class MappedStateFlow<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R,
) : StateFlow<R> {
    override val value: R get() = transform(source.value)

    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        source.map(transform).distinctUntilChanged().collect(collector)
        // The source is a StateFlow and never completes; this only satisfies the signature.
        awaitCancellation()
    }
}
