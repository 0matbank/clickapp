package com.clickdownloader.core.extractor

import android.media.MediaCodecList
import com.clickdownloader.core.model.FormatCompatibility
import com.clickdownloader.core.model.MediaFormatOption

object AndroidFormatCompatibility {
    fun evaluate(format: MediaFormatOption): FormatCompatibility {
        if (format.drmProtected) return FormatCompatibility.UNSUPPORTED_DRM
        val availableTypes = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                .filterNot { it.isEncoder }
                .flatMap { it.supportedTypes.asList() }
                .map(String::lowercase)
                .toSet()
        }.getOrDefault(emptySet())
        val videoSupported = codecMime(format.videoCodec, video = true)?.let { it in availableTypes } ?: !format.hasVideo
        val audioSupported = codecMime(format.audioCodec, video = false)?.let { it in availableTypes } ?: !format.hasAudio
        if (!videoSupported || !audioSupported) return FormatCompatibility.TRANSCODE_REQUIRED
        if (!format.isProgressive || format.extension?.lowercase() !in setOf("mp4", "webm", "m4a", "mp3")) {
            return FormatCompatibility.REMUX_REQUIRED
        }
        return FormatCompatibility.DIRECT
    }

    private fun codecMime(codec: String?, video: Boolean): String? {
        val value = codec?.lowercase() ?: return null
        return when {
            value.startsWith("avc") || value.startsWith("h264") -> "video/avc"
            value.startsWith("hev") || value.startsWith("hvc") || value.startsWith("h265") -> "video/hevc"
            value.startsWith("vp9") || value.startsWith("vp09") -> "video/x-vnd.on2.vp9"
            value.startsWith("vp8") || value.startsWith("vp08") -> "video/x-vnd.on2.vp8"
            value.startsWith("av01") -> "video/av01"
            value.startsWith("mp4a") || value.startsWith("aac") -> "audio/mp4a-latm"
            value.startsWith("opus") -> "audio/opus"
            value.startsWith("vorbis") -> "audio/vorbis"
            value.startsWith("mp3") -> "audio/mpeg"
            video -> "video/$value"
            else -> "audio/$value"
        }
    }
}
