package com.clickdownloader.core.extractor

import com.clickdownloader.core.domain.MediaExtractor
import com.clickdownloader.core.model.MediaAnalysis
import com.clickdownloader.core.model.MediaFormatOption
import com.clickdownloader.core.model.MediaMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ExactFormatRefresherTest {
    @Test fun `refresh preserves every exact selected format id`() = runTest {
        val result = ExactFormatRefresher(FakeExtractor(listOf("401", "140"))).refresh("https://example.com/watch", "401+140", null)
        assertEquals(listOf("401", "140"), result.formats.map { it.formatId })
    }

    @Test fun `refresh never silently substitutes another quality`() = runTest {
        try {
            ExactFormatRefresher(FakeExtractor(listOf("399", "140"))).refresh("https://example.com/watch", "401+140", null)
            fail("Expected the exact format refresh to fail")
        } catch (_: ExactFormatUnavailableException) {
            // Expected: no fallback format may replace the user's exact selection.
        }
    }

    private class FakeExtractor(ids: List<String>) : MediaExtractor {
        private val analysis = MediaAnalysis(
            MediaMetadata("id", "title", canonicalUrl = "https://example.com/watch"),
            ids.map { MediaFormatOption(it, hasVideo = it != "140", hasAudio = it == "140") },
            webpageUrl = "https://example.com/watch",
        )
        override suspend fun analyze(url: String, cookieFilePath: String?) = analysis
        override fun cancel(operationId: String) = false
        override fun engineVersion() = "test"
    }
}
