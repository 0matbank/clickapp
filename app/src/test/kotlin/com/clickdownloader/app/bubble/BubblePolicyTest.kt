package com.clickdownloader.app.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubblePolicyTest {
    @Test fun extractsOnlyHttpUrls() {
        assertEquals("https://example.com/watch?v=1", BubblePolicy.firstSupportedUrl("copy https://example.com/watch?v=1."))
        assertNull(BubblePolicy.firstSupportedUrl("javascript:alert(1)"))
    }

    @Test fun allowlistOnlyAppliesToExplicitAccessibilityAssist() {
        assertTrue(BubblePolicy.shouldShowForPackage(false, setOf("video.app"), "other.app", "click.app"))
        assertTrue(BubblePolicy.shouldShowForPackage(true, setOf("video.app"), "video.app", "click.app"))
        assertFalse(BubblePolicy.shouldShowForPackage(true, setOf("video.app"), "other.app", "click.app"))
        assertFalse(BubblePolicy.shouldShowForPackage(false, emptySet(), "click.app", "click.app"))
    }
}
