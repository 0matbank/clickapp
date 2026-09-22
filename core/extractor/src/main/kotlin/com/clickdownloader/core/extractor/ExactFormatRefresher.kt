package com.clickdownloader.core.extractor

import com.clickdownloader.core.domain.MediaExtractor
import com.clickdownloader.core.model.MediaAnalysis

class ExactFormatUnavailableException(message: String) : Exception(message)

class ExactFormatRefresher(private val extractor: MediaExtractor) {
    suspend fun refresh(sourceUrl: String, exactFormatSpec: String, cookieFilePath: String?): MediaAnalysis {
        val analysis = extractor.analyze(sourceUrl, cookieFilePath)
        val requiredIds = exactFormatSpec.split('+').filter(String::isNotBlank).toSet()
        val availableIds = analysis.formats.mapTo(hashSetOf()) { it.formatId }
        val missing = requiredIds - availableIds
        if (missing.isNotEmpty()) throw ExactFormatUnavailableException(
            "The selected source format is no longer available: ${missing.sorted().joinToString()}",
        )
        return analysis
    }
}
