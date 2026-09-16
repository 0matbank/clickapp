package com.clickdownloader.core.extractor

import android.media.MediaCodecList
import android.media.MediaCodecInfo
import com.clickdownloader.core.model.FormatCompatibility
import com.clickdownloader.core.model.MediaFormatOption

object AndroidFormatCompatibility {
    fun evaluate(format: MediaFormatOption): FormatCompatibility {
        if (format.drmProtected) return FormatCompatibility.UNSUPPORTED_DRM
        val decoders = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filterNot { it.isEncoder } }.getOrDefault(emptyList())
        val videoSupported = codecMime(format.videoCodec, video = true)?.let { mime ->
            decoders.any { decoder -> decoder.supportsVideo(mime, format) }
        } ?: !format.hasVideo
        val audioSupported = codecMime(format.audioCodec, video = false)?.let { mime ->
            decoders.any { decoder -> decoder.supportedTypes.any { it.equals(mime, true) } }
        } ?: !format.hasAudio
        if (!videoSupported || !audioSupported) return FormatCompatibility.TRANSCODE_REQUIRED
        if (!format.isProgressive || format.extension?.lowercase() !in setOf("mp4", "webm", "m4a", "mp3")) {
            return FormatCompatibility.REMUX_REQUIRED
        }
        return FormatCompatibility.DIRECT
    }

    private fun MediaCodecInfo.supportsVideo(mime: String, format: MediaFormatOption): Boolean {
        val actualType = supportedTypes.firstOrNull { it.equals(mime, true) } ?: return false
        val capabilities = runCatching { getCapabilitiesForType(actualType) }.getOrNull() ?: return false
        val width = format.width
        val height = format.height
        val fps = format.framesPerSecond
        if (width != null && height != null) {
            val videoCapabilities = capabilities.videoCapabilities ?: return false
            val supported = runCatching {
                if (fps != null && fps > 0) videoCapabilities.areSizeAndRateSupported(width, height, fps)
                else videoCapabilities.isSizeSupported(width, height)
            }.getOrDefault(false)
            if (!supported) return false
        }
        if (format.dynamicRange?.contains("HDR", true) == true || format.dynamicRange?.contains("HLG", true) == true) {
            val hdrProfiles = setOf(
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                MediaCodecInfo.CodecProfileLevel.VP9Profile2,
                MediaCodecInfo.CodecProfileLevel.VP9Profile3,
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
            )
            if (capabilities.profileLevels.none { it.profile in hdrProfiles }) return false
        }
        return true
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
