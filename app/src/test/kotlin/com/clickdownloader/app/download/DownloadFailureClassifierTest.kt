package com.clickdownloader.app.download

import com.clickdownloader.core.model.DirectDownloadFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadFailureClassifierTest {
    @Test fun `expired auth rate limit server and corruption failures are distinct`() {
        assertEquals(DirectDownloadFailure.AUTH_REQUIRED, classify("HTTP 401 authentication required"))
        assertEquals(DirectDownloadFailure.LINK_EXPIRED, classify("HTTP 403 signed URL expired"))
        assertEquals(DirectDownloadFailure.LINK_EXPIRED, classify("HTTP 410"))
        assertEquals(DirectDownloadFailure.RATE_LIMITED, classify("HTTP 429 rate limit"))
        assertEquals(DirectDownloadFailure.NETWORK, classify("HTTP 503"))
        assertEquals(DirectDownloadFailure.VERIFICATION, classify("corrupt fragment"))
    }

    @Test fun `stored processing errors redact signed credentials`() {
        val result = DownloadFailureClassifier.classify(IllegalStateException("failed https://cdn.test/a?X-Amz-Signature=secret"))
        assertFalse(result.message.orEmpty().contains("secret"))
    }

    private fun classify(message: String) = DownloadFailureClassifier.classify(IllegalStateException(message)).failure
}
