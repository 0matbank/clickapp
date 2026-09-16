package com.clickdownloader.core.media

import android.media.MediaExtractor
import android.media.MediaFormat
import com.clickdownloader.core.model.SelectedFormat
import java.io.File

data class ArtifactInspection(
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val width: Int?,
    val height: Int?,
    val durationMicros: Long?,
)

object MediaArtifactVerifier {
    fun verify(file: File, selected: SelectedFormat): ArtifactInspection {
        require(file.isFile && file.length() > 0) { "Final media artifact is missing or empty" }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var hasVideo = false
            var hasAudio = false
            var width: Int? = null
            var height: Int? = null
            var duration: Long? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    hasVideo = true
                    width = format.integerOrNull(MediaFormat.KEY_WIDTH)
                    height = format.integerOrNull(MediaFormat.KEY_HEIGHT)
                }
                if (mime.startsWith("audio/")) hasAudio = true
                format.longOrNull(MediaFormat.KEY_DURATION)?.let { value -> duration = maxOf(duration ?: 0, value) }
            }
            require(hasVideo == selected.hasVideo) { "Video-track verification did not match the exact selection" }
            require(hasAudio == selected.hasAudio) { "Audio-track verification did not match the exact selection" }
            if (selected.width != null && width != null) require(width == selected.width) { "Output width changed from ${selected.width} to $width" }
            if (selected.height != null && height != null) require(height == selected.height) { "Output height changed from ${selected.height} to $height" }
            return ArtifactInspection(hasVideo, hasAudio, width, height, duration)
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.integerOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null
    private fun MediaFormat.longOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null
}
