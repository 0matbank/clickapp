package com.clickdownloader.core.media

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ConversionPreflight(
    val sourceBytes: Long,
    val requiredFreeBytes: Long,
    val availableBytes: Long,
    val estimatedMillis: Long,
    val batteryPercent: Int?,
    val deviceHot: Boolean,
)

data class CompatibleCopyResult(
    val output: File,
    val originalSha256: String,
    val outputInspection: ArtifactInspection,
)

class CompatibleCopyProcessor(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun preflight(uri: Uri): ConversionPreflight = withContext(Dispatchers.IO) {
        val sourceBytes = resolver.openFileDescriptor(uri, "r")?.use { it.statSize.coerceAtLeast(0) }
            ?: error("Original media is unavailable")
        val required = max(256L * 1024 * 1024, sourceBytes * 2 + 64L * 1024 * 1024)
        val available = StatFs(appContext.cacheDir.absolutePath).availableBytes
        val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val hot = if (Build.VERSION.SDK_INT >= 29) {
            appContext.getSystemService(PowerManager::class.java).currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        } else false
        ConversionPreflight(
            sourceBytes,
            required,
            available,
            estimatedMillis = max(60_000L, sourceBytes / (4L * 1024 * 1024) * 1_000L),
            batteryPercent = percent,
            deviceHot = hot,
        )
    }

    suspend fun convert(
        uri: Uri,
        workDirectory: File,
        allowLowBattery: Boolean,
        allowWhenHot: Boolean,
        onProgress: (String) -> Unit = {},
    ): CompatibleCopyResult = withContext(Dispatchers.IO) {
        FfmpegRuntime.initialize(appContext)
        val preflight = preflight(uri)
        require(preflight.availableBytes >= preflight.requiredFreeBytes) { "Not enough temporary storage for a compatible copy" }
        require(allowLowBattery || preflight.batteryPercent == null || preflight.batteryPercent >= 15) { "Battery is below 15%; connect a charger or allow low-battery conversion" }
        require(allowWhenHot || !preflight.deviceHot) { "Device is hot; wait for it to cool or explicitly allow hot-device conversion" }
        workDirectory.mkdirs()
        val input = File(workDirectory, "original-input")
        val output = File(workDirectory, "compatible-output.mp4")
        resolver.openInputStream(uri)!!.use { source -> input.outputStream().use(source::copyTo) }
        val originalHash = sha256(input)
        onProgress("Converting to H.264/AAC without changing resolution")
        val ffmpeg = File(appContext.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val command = listOf(
            ffmpeg.absolutePath, "-y", "-hide_banner", "-loglevel", "warning", "-i", input.absolutePath,
            "-map", "0:v:0?", "-map", "0:a:0?", "-map_metadata", "0", "-map_chapters", "0",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-profile:a", "aac_low", "-b:a", "192k", "-movflags", "+faststart",
            output.absolutePath,
        )
        val process = ProcessBuilder(command).redirectErrorStream(true).apply {
            environment()["LD_LIBRARY_PATH"] = FfmpegRuntime.libraryPath(appContext)
        }.start()
        val log = process.inputStream.bufferedReader().use { it.readText().takeLast(8_000) }
        check(process.waitFor() == 0 && output.length() > 0) { "Compatible conversion failed: $log" }
        val afterHash = resolver.openInputStream(uri)!!.use(::sha256)
        check(originalHash == afterHash) { "Original file changed during compatible-copy creation" }
        val sourceInspection = inspect(input, requireAvc = false)
        val outputInspection = inspect(output, requireAvc = true)
        require(outputInspection.hasVideo == sourceInspection.hasVideo) { "Compatible output video track mismatch" }
        require(outputInspection.hasAudio == sourceInspection.hasAudio) { "Compatible output audio track mismatch" }
        if (sourceInspection.hasVideo) {
            require(outputInspection.width == sourceInspection.width && outputInspection.height == sourceInspection.height) {
                "Compatible output resolution changed without permission"
            }
        }
        input.delete()
        CompatibleCopyResult(output, originalHash, outputInspection)
    }

    private fun inspect(file: File, requireAvc: Boolean): ArtifactInspection {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var video = false
            var audio = false
            var width: Int? = null
            var height: Int? = null
            var duration: Long? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    video = true
                    width = format.intOrNull(MediaFormat.KEY_WIDTH)
                    height = format.intOrNull(MediaFormat.KEY_HEIGHT)
                    if (requireAvc) require(mime == "video/avc") { "Compatible output is not H.264/AVC" }
                }
                if (mime.startsWith("audio/")) {
                    audio = true
                    if (requireAvc) require(mime == "audio/mp4a-latm") { "Compatible output is not AAC-LC" }
                }
                if (format.containsKey(MediaFormat.KEY_DURATION)) duration = max(duration ?: 0, format.getLong(MediaFormat.KEY_DURATION))
            }
            require(video || audio) { "Compatible output has no playable tracks" }
            return ArtifactInspection(video, audio, width, height, duration)
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.intOrNull(key: String) = if (containsKey(key)) getInteger(key) else null
    private fun sha256(file: File): String = file.inputStream().use(::sha256)
    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
