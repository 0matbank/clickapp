package com.clickdownloader.core.download

import com.clickdownloader.core.model.DirectDownloadException
import com.clickdownloader.core.model.DirectDownloadFailure
import java.io.File

object DirectFileVerifier {
    fun verify(file: File, expectedBytes: Long?): Long {
        if (!file.isFile || file.length() <= 0) throw DirectDownloadException(
            DirectDownloadFailure.VERIFICATION,
            "The downloaded file is empty or missing",
        )
        if (expectedBytes != null && file.length() != expectedBytes) throw DirectDownloadException(
            DirectDownloadFailure.VERIFICATION,
            "The downloaded file is incomplete",
        )
        file.inputStream().use { input ->
            require(input.read() >= 0) { "The downloaded file cannot be read" }
        }
        return file.length()
    }
}
