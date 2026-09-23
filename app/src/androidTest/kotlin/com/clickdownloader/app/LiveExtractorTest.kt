package com.clickdownloader.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clickdownloader.core.extractor.YtDlpExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveExtractorTest {
    @Test
    fun publicReferencePageReturnsUnfilteredSourceFormats() = runBlocking {
        val result = YtDlpExtractor(ApplicationProvider.getApplicationContext())
            .analyze("https://storage.googleapis.com/shaka-demo-assets/sintel-mp4-only/dash.mpd")

        assertTrue(result.metadata.title.isNotBlank())
        assertTrue(result.formats.isNotEmpty())
        assertEquals(result.formats.size, result.formats.map { it.formatId }.distinct().size)
        assertTrue(result.formats.any { (it.height ?: 0) >= 1600 })
        assertTrue(result.formats.any { it.hasAudio })
    }
}
