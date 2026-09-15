package com.clickdownloader.core.download

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameAndRetryPolicyTest {
    @Test
    fun `filename strips traversal and reserved characters`() {
        val name = FilenamePolicy.sanitize("../bad:name?.mp4")
        assertFalse(name.contains('/'))
        assertFalse(name.contains(':'))
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun `retry is bounded exponential with jitter`() {
        val policy = RetryPolicy(baseDelayMillis = 1_000, maximumDelayMillis = 10_000, random = Random(1))
        assertTrue(policy.delayMillis(0) in 1_000..1_333)
        assertTrue(policy.delayMillis(1) in 2_000..2_666)
        assertEquals(10_000, policy.delayMillis(20))
    }
}
