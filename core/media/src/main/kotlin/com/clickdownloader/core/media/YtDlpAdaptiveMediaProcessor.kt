package com.clickdownloader.core.media

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.clickdownloader.core.domain.AdaptiveMediaProcessor
import com.clickdownloader.core.model.FragmentCheckpoint
import com.clickdownloader.core.model.MediaProcessProgress
import com.clickdownloader.core.model.ProcessedMediaArtifact
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class YtDlpAdaptiveMediaProcessor(context: Context) : AdaptiveMediaProcessor {
    private val appContext = context.applicationContext

    override suspend fun process(
        jobId: String,
        sourceUrl: String,
        exactFormatSpec: String,
        workingDirectory: File,
        preferredContainer: String?,
        onProgress: suspend (MediaProcessProgress) -> Unit,
        onCheckpoint: suspend (FragmentCheckpoint) -> Unit,
    ): ProcessedMediaArtifact = withContext(Dispatchers.IO) {
        ensureInitialized()
        workingDirectory.mkdirs()
        val request = YtDlpMediaCommand.build(sourceUrl, exactFormatSpec, workingDirectory, jobId, preferredContainer)
        try {
            YoutubeDL.getInstance().execute(request, jobId) { percent, eta, line ->
                runBlocking {
                    onProgress(MediaProcessProgress(percent, eta, line))
                    FragmentCheckpointScanner.scan(jobId, exactFormatSpec, workingDirectory).forEach { onCheckpoint(it) }
                }
            }
        } catch (error: Throwable) {
            if (liveFinalizationRequests.remove(jobId)) {
                return@withContext LiveStreamFinalizer(appContext).finalize(jobId, workingDirectory, preferredContainer)
            }
            throw error
        }
        liveFinalizationRequests.remove(jobId)
        val media = workingDirectory.listFiles().orEmpty()
            .filter { it.isFile && it.length() > 0 && it.extension.lowercase() in MEDIA_EXTENSIONS }
            .maxByOrNull(File::lastModified)
            ?: error("The media processor produced no final media file")
        val sidecars = workingDirectory.listFiles().orEmpty()
            .filter { it.isFile && it != media && !it.name.endsWith(".part") && !it.name.contains(".part-Frag") }
            .map(File::getAbsolutePath)
        ProcessedMediaArtifact(media.absolutePath, sidecars)
    }

    override fun cancel(jobId: String): Boolean = YoutubeDL.getInstance().destroyProcessById(jobId)

    override fun requestLiveFinalization(jobId: String): Boolean {
        liveFinalizationRequests += jobId
        return YoutubeDL.getInstance().destroyProcessById(jobId)
    }

    @Synchronized
    private fun ensureInitialized() {
        if (!initialized) {
            YoutubeDL.getInstance().init(appContext)
            FFmpeg.getInstance().init(appContext)
            initialized = true
        }
    }

    private companion object {
        @Volatile var initialized = false
        val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "m4a", "mp3", "opus", "ogg", "mov", "ts", "aac", "flac")
        val liveFinalizationRequests = ConcurrentHashMap.newKeySet<String>()
    }
}

object FragmentCheckpointScanner {
    private val pattern = Regex("(?:Frag|frag)(\\d+)")

    fun scan(jobId: String, trackId: String, directory: File): List<FragmentCheckpoint> =
        directory.listFiles().orEmpty().mapNotNull { file ->
            val match = pattern.find(file.name) ?: return@mapNotNull null
            FragmentCheckpoint(
                jobId = jobId,
                trackId = trackId,
                fragmentIndex = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null,
                downloadedBytes = file.length(),
                temporaryPath = file.absolutePath,
                completed = !file.name.endsWith(".part"),
            )
        }
}

object YtDlpMediaCommand {
    fun build(
        sourceUrl: String,
        exactFormatSpec: String,
        workingDirectory: File,
        jobId: String,
        preferredContainer: String?,
    ): YoutubeDLRequest = YoutubeDLRequest(sourceUrl).apply {
        addOption("--no-playlist")
        addOption("--format", exactFormatSpec)
        addOption("--output", File(workingDirectory, "$jobId.%(ext)s").absolutePath)
        addOption("--continue")
        addOption("--part")
        addOption("--keep-fragments")
        addOption("--hls-use-mpegts")
        addOption("--newline")
        addOption("--embed-metadata")
        addOption("--write-thumbnail")
        addOption("--embed-thumbnail")
        addOption("--write-subs")
        addOption("--embed-subs")
        addOption("--sub-langs", "all")
        addOption("--write-info-json")
        if (preferredContainer in setOf("mp4", "mkv", "webm")) addOption("--merge-output-format", preferredContainer!!)
    }
}

class LiveStreamFinalizer(private val context: Context) {
    fun finalize(jobId: String, directory: File, preferredContainer: String?): ProcessedMediaArtifact {
        val extension = preferredContainer?.takeIf { it in setOf("mp4", "mkv", "webm") } ?: "mkv"
        val output = File(directory, "$jobId-live.$extension")
        val input = directory.listFiles().orEmpty()
            .filter { it.isFile && it.length() > 0 && it != output && !it.name.endsWith(".json") && it.extension.lowercase() !in setOf("jpg", "jpeg", "png", "webp", "vtt", "srt") }
            .maxByOrNull(File::length)
            ?: error("No captured live media is available to finalize")
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val process = ProcessBuilder(ffmpeg.absolutePath, "-y", "-hide_banner", "-loglevel", "error", "-i", input.absolutePath, "-map", "0", "-c", "copy", output.absolutePath)
            .redirectErrorStream(true)
            .apply {
                environment()["LD_LIBRARY_PATH"] = listOf(
                    context.applicationInfo.nativeLibraryDir,
                    File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").absolutePath,
                ).joinToString(":")
            }
            .start()
        val outputText = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0 && output.length() > 0) { "Live stream finalization failed: $outputText" }
        return ProcessedMediaArtifact(output.absolutePath, directory.listFiles().orEmpty().filter { it != output }.map(File::getAbsolutePath))
    }
}
