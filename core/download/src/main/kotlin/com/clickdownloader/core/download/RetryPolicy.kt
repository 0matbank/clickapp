package com.clickdownloader.core.download

import kotlin.math.min
import kotlin.random.Random

class RetryPolicy(
    private val baseDelayMillis: Long = 1_000,
    private val maximumDelayMillis: Long = 60_000,
    private val random: Random = Random.Default,
) {
    fun delayMillis(attempt: Int): Long {
        require(attempt >= 0)
        val exponential = baseDelayMillis * (1L shl min(attempt, 16))
        val capped = min(exponential, maximumDelayMillis)
        val jitter = if (capped <= 1) 0 else random.nextLong(0, capped / 3 + 1)
        return min(capped + jitter, maximumDelayMillis)
    }
}
