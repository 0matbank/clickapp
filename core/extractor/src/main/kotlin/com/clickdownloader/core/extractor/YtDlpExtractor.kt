package com.clickdownloader.core.extractor

import android.content.Context
import com.fasterxml.jackson.databind.JsonNode
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.clickdownloader.core.domain.MediaExtractor
import com.clickdownloader.core.model.LiveStatus
import com.clickdownloader.core.model.MediaAnalysis
import com.clickdownloader.core.model.MediaFormatOption
import com.clickdownloader.core.model.MediaMetadata
import com.clickdownloader.core.model.PlaylistItem
import com.clickdownloader.core.model.StreamProtocol
import com.clickdownloader.core.model.SubtitleTrack
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YtDlpExtractor(context: Context) : MediaExtractor {
    private val appContext = context.applicationContext

    override suspend fun analyze(url: String, cookieFilePath: String?): MediaAnalysis = withContext(Dispatchers.IO) {
        executeAnalysis(url, cookieFilePath, playlist = false)
    }

    override suspend fun analyzePlaylist(url: String, cookieFilePath: String?): MediaAnalysis = withContext(Dispatchers.IO) {
        executeAnalysis(url, cookieFilePath, playlist = true)
    }

    private fun executeAnalysis(url: String, cookieFilePath: String?, playlist: Boolean): MediaAnalysis {
        ensureInitialized()
        val operationId = UUID.randomUUID().toString()
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-warnings")
            if (playlist) {
                addOption("--yes-playlist")
                addOption("--flat-playlist")
            } else {
                addOption("--no-playlist")
            }
            addOption("--skip-download")
            if (cookieFilePath != null) addOption("--cookies", cookieFilePath)
        }
        val response = YoutubeDL.getInstance().execute(request, operationId)
        return YtDlpJsonNormalizer.normalize(YoutubeDL.objectMapper.readTree(response.out), url)
    }

    override fun cancel(operationId: String): Boolean = YoutubeDL.getInstance().destroyProcessById(operationId)

    override fun engineVersion(): String? = runCatching { YoutubeDL.versionName(appContext) }.getOrNull()

    @Synchronized
    private fun ensureInitialized() {
        if (!initialized) {
            YoutubeDL.getInstance().init(appContext)
            initialized = true
        }
    }

    private companion object {
        @Volatile var initialized = false
    }
}

object YtDlpJsonNormalizer {
    fun normalize(root: JsonNode, requestedUrl: String): MediaAnalysis {
        val id = root.text("id") ?: "analysis"
        val canonicalUrl = root.text("webpage_url") ?: root.text("original_url") ?: requestedUrl
        val thumbnails = root.path("thumbnails").takeIf(JsonNode::isArray)
            ?.mapNotNull { it.text("url") }
            .orEmpty()
        val metadata = MediaMetadata(
            jobId = id,
            title = root.text("title") ?: canonicalUrl,
            description = root.text("description"),
            creator = root.text("channel") ?: root.text("uploader") ?: root.text("creator"),
            sourcePlatform = root.text("extractor_key") ?: root.text("extractor"),
            canonicalUrl = canonicalUrl,
            thumbnailUrls = thumbnails,
            durationMillis = root.number("duration")?.times(1_000)?.toLong(),
            uploadDate = root.text("upload_date"),
            liveStatus = when (root.text("live_status")) {
                "is_live" -> LiveStatus.LIVE
                "was_live", "post_live" -> LiveStatus.WAS_LIVE
                "is_upcoming" -> LiveStatus.UPCOMING
                else -> LiveStatus.NOT_LIVE
            },
        )
        val formats = root.path("formats").takeIf(JsonNode::isArray)
            ?.mapIndexed { index, node -> node.asFormat(index) }
            .orEmpty()
        val subtitles = parseSubtitles(root.path("subtitles"), false) + parseSubtitles(root.path("automatic_captions"), true)
        val playlistItems = root.path("entries").takeIf(JsonNode::isArray)?.mapIndexedNotNull { index, entry ->
            val entryUrl = entry.text("webpage_url") ?: entry.text("url") ?: entry.text("original_url")
            entryUrl?.let {
                PlaylistItem(
                    id = entry.text("id") ?: "item-$index",
                    sourceUrl = it,
                    title = entry.text("title") ?: it,
                    position = index,
                    durationMillis = entry.number("duration")?.times(1_000)?.toLong(),
                    thumbnailUrl = entry.text("thumbnail"),
                )
            }
        }.orEmpty()
        return MediaAnalysis(
            metadata = metadata,
            formats = formats,
            subtitles = subtitles,
            extractorKey = root.text("extractor_key"),
            webpageUrl = canonicalUrl,
            isPlaylist = root.text("_type") == "playlist",
            playlistItems = playlistItems,
        )
    }

    private fun JsonNode.asFormat(index: Int): MediaFormatOption {
        val protocolValue = text("protocol").orEmpty().lowercase()
        val protocol = when {
            "m3u8" in protocolValue -> StreamProtocol.HLS
            "dash" in protocolValue || "http_dash_segments" in protocolValue -> StreamProtocol.DASH
            protocolValue.startsWith("http") || protocolValue.isBlank() -> StreamProtocol.HTTP
            else -> StreamProtocol.OTHER
        }
        val videoCodec = text("vcodec").takeUnless { it == "none" }
        val audioCodec = text("acodec").takeUnless { it == "none" }
        val directUrl = text("url")
        return MediaFormatOption(
            formatId = text("format_id") ?: "unknown-$index",
            formatNote = text("format_note") ?: text("format"),
            extension = text("ext"),
            protocol = protocol,
            directUrl = directUrl.takeIf { protocol == StreamProtocol.HTTP },
            manifestUrl = directUrl.takeIf { protocol == StreamProtocol.HLS || protocol == StreamProtocol.DASH },
            width = integer("width"),
            height = integer("height"),
            framesPerSecond = number("fps"),
            videoCodec = videoCodec,
            videoBitrate = number("vbr")?.times(1_000)?.toLong(),
            dynamicRange = text("dynamic_range"),
            bitDepth = integer("bit_depth"),
            audioCodec = audioCodec,
            audioBitrate = number("abr")?.times(1_000)?.toLong(),
            audioSampleRate = number("asr")?.toLong(),
            audioChannels = integer("audio_channels"),
            audioLanguage = text("language"),
            estimatedBytes = long("filesize") ?: long("filesize_approx"),
            isSizeApproximate = long("filesize") == null && long("filesize_approx") != null,
            hasVideo = videoCodec != null,
            hasAudio = audioCodec != null,
            drmProtected = bool("has_drm") == true,
            httpHeaders = path("http_headers").takeIf(JsonNode::isObject)?.fields()?.asSequence()
                ?.associate { it.key to it.value.asText() }.orEmpty(),
        )
    }

    private fun parseSubtitles(node: JsonNode, automatic: Boolean): List<SubtitleTrack> {
        if (!node.isObject) return emptyList()
        return node.fields().asSequence().flatMap { (language, entries) ->
            if (!entries.isArray) emptySequence() else entries.asSequence().map { entry ->
                SubtitleTrack(
                    language = language,
                    name = entry.text("name"),
                    extension = entry.text("ext"),
                    url = entry.text("url"),
                    isAutomatic = automatic,
                )
            }
        }.toList()
    }

    private fun JsonNode.text(name: String): String? = path(name).takeUnless { it.isMissingNode || it.isNull }?.asText()?.takeIf(String::isNotBlank)
    private fun JsonNode.number(name: String): Double? = path(name).takeIf(JsonNode::isNumber)?.asDouble()
    private fun JsonNode.integer(name: String): Int? = path(name).takeIf(JsonNode::isIntegralNumber)?.asInt()
    private fun JsonNode.long(name: String): Long? = path(name).takeIf(JsonNode::isIntegralNumber)?.asLong()
    private fun JsonNode.bool(name: String): Boolean? = path(name).takeIf(JsonNode::isBoolean)?.asBoolean()
}
