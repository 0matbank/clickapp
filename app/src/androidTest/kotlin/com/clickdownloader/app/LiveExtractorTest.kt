package com.clickdownloader.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clickdownloader.app.extractor.IsolatedAnalysisClient
import com.clickdownloader.core.extractor.YtDlpExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveExtractorTest {
    @Test
    fun userReportedYouTubePageReturnsFormats() = runBlocking {
        val result = YtDlpExtractor(ApplicationProvider.getApplicationContext())
            .analyze("https://youtu.be/k7wyo43DMGI?si=fK2Nt5MdPlqlED")

        assertTrue(result.metadata.title.isNotBlank())
        assertTrue(result.formats.isNotEmpty())
        assertTrue(result.formats.any { it.hasVideo })
        assertTrue(result.formats.any { it.hasAudio })
    }

    @Test
    fun unavailableYouTubePageReportsTheActualReason() = runBlocking {
        val error = runCatching {
            YtDlpExtractor(ApplicationProvider.getApplicationContext())
                .analyze("https://youtube.com/watch?v=mSQTCl4dvtQ")
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("video is unavailable", ignoreCase = true))
    }

    @Test
    fun isolatedEngineReturnsYouTubeFormats() = runBlocking {
        val result = IsolatedAnalysisClient(ApplicationProvider.getApplicationContext())
            .analyze("https://youtu.be/k7wyo43DMGI", null, null)

        assertTrue(result.analysis.formats.any { it.hasVideo })
        assertTrue(result.analysis.formats.any { it.hasAudio })
    }

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
