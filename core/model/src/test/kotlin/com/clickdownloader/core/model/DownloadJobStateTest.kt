package com.clickdownloader.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadJobStateTest {
    @Test
    fun `happy path reaches completed only through verification`() {
        val path = listOf(
            DownloadJobState.CREATED,
            DownloadJobState.ANALYZING,
            DownloadJobState.WAITING_FOR_SELECTION,
            DownloadJobState.QUEUED,
            DownloadJobState.PREPARING,
            DownloadJobState.DOWNLOADING_VIDEO,
            DownloadJobState.MERGING,
            DownloadJobState.VERIFYING,
            DownloadJobState.COMPLETED,
        )

        path.zipWithNext().forEach { (current, next) ->
            assertTrue("$current should transition to $next", current.canTransitionTo(next))
        }
        assertFalse(DownloadJobState.DOWNLOADING_VIDEO.canTransitionTo(DownloadJobState.COMPLETED))
        assertFalse(DownloadJobState.MERGING.canTransitionTo(DownloadJobState.COMPLETED))
    }

    @Test
    fun `terminal states cannot restart silently`() {
        assertFalse(DownloadJobState.COMPLETED.canTransitionTo(DownloadJobState.QUEUED))
        assertFalse(DownloadJobState.CANCELLED.canTransitionTo(DownloadJobState.ANALYZING))
    }

    @Test
    fun `recoverable interruptions can return through queue`() {
        assertTrue(DownloadJobState.DOWNLOADING_FRAGMENTS.canTransitionTo(DownloadJobState.WAITING_FOR_NETWORK))
        assertTrue(DownloadJobState.WAITING_FOR_NETWORK.canTransitionTo(DownloadJobState.QUEUED))
        assertTrue(DownloadJobState.LINK_EXPIRED.canTransitionTo(DownloadJobState.ANALYZING))
    }
}

