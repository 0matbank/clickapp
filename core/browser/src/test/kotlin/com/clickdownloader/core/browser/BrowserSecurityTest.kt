package com.clickdownloader.core.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserSecurityTest {
    @Test
    fun `detector recognizes manifests media and redacts signed query`() {
        val detected = MediaRequestDetector.detect("https://cdn.test/live/master.m3u8?token=secret&quality=4k")
        assertNotNull(detected)
        assertEquals("m3u8", detected!!.kind)
        assertFalse(detected.displayUrl.contains("secret"))
    }

    @Test
    fun `detector ignores ordinary page and redactor removes headers`() {
        assertNull(MediaRequestDetector.detect("https://site.test/article"))
        val redacted = SecretRedactor.redact("Cookie: SID=secret\nAuthorization: Bearer hidden")
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("hidden"))
    }
}
