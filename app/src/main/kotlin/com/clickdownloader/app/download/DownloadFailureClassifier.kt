package com.clickdownloader.app.download

import com.clickdownloader.core.browser.SecretRedactor
import com.clickdownloader.core.model.DirectDownloadException
import com.clickdownloader.core.model.DirectDownloadFailure

object DownloadFailureClassifier {
    fun classify(error: Throwable): DirectDownloadException {
        val message = error.message ?: "Media processing failed"
        val lower = message.lowercase()
        val failure = when {
            "401" in lower || "login" in lower || "authentication" in lower -> DirectDownloadFailure.AUTH_REQUIRED
            "403" in lower || "410" in lower || "expired" in lower -> DirectDownloadFailure.LINK_EXPIRED
            "429" in lower || "rate limit" in lower -> DirectDownloadFailure.RATE_LIMITED
            "network" in lower || "timed out" in lower || "connection" in lower || "unable to download" in lower || "http 5" in lower -> DirectDownloadFailure.NETWORK
            else -> DirectDownloadFailure.VERIFICATION
        }
        return DirectDownloadException(failure, SecretRedactor.redact(message), error)
    }
}
