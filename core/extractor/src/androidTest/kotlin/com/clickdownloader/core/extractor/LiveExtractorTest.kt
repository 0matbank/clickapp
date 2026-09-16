package com.clickdownloader.core.extractor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            .analyze("https://vimeo.com/22439234")

        assertTrue(result.metadata.title.isNotBlank())
        assertTrue(result.formats.isNotEmpty())
        assertEquals(result.formats.size, result.formats.map { it.formatId }.distinct().size)
    }
}
