package io.github.parthibdebnath.openplayer.audio

import io.github.parthibdebnath.openplayer.util.stableHash64

/**
 * SplitMix64: the random numbers behind a shuffle. Written out here rather than borrowed from
 * kotlin.random, whose algorithm is an implementation detail free to change between Kotlin
 * releases - and with it every shuffle a seed stands for. A shuffle seed has to mean the same
 * order forever.
 */
class ShuffleRandom(seed: Long) {
    private var state = seed

    constructor(seed: String) : this(stableHash64(seed))

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xbf58476d1ce4e5b9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94d049bb133111eb
        return z xor (z ushr 31)
    }

    /** Evenly in 0 until [bound]: draws past the last whole multiple of [bound] are thrown back. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        val limit = Long.MAX_VALUE - Long.MAX_VALUE % bound
        while (true) {
            val draw = nextLong() ushr 1
            if (draw < limit) return (draw % bound).toInt()
        }
    }

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9e3779b97f4a7c15
    }
}

/** Fisher-Yates over [items] in place, from the end down. */
fun <T> MutableList<T>.shuffleWith(random: ShuffleRandom) {
    for (i in lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        if (i != j) this[i] = this[j].also { this[j] = this[i] }
    }
}
