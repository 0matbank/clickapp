package com.clickdownloader.core.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clickdownloader.core.model.SelectedFormat
import com.yausername.ffmpeg.FFmpeg
import java.io.File
import java.security.MessageDigest
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaArtifactVerifierTest {
    @Test
    fun downloadsAndLosslesslyMergesPublic4kSourceTracks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FFmpeg.getInstance().init(context)
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val directory = File(context.cacheDir, "public-4k-${System.nanoTime()}").apply { mkdirs() }
        val merged = File(directory, "public-4k-merged.mp4")

        // Public, non-DRM DASH source tracks maintained for Shaka Player interoperability tests.
        execute(
            context,
            ffmpeg,
            "-t", "1",
            "-i", "https://storage.googleapis.com/shaka-demo-assets/sintel-mp4-only/v-2160p-17000k-libx264.mp4",
            "-t", "1",
            "-i", "https://storage.googleapis.com/shaka-demo-assets/sintel-mp4-only/a-eng-0128k-aac.mp4",
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-c", "copy",
            "-shortest",
            merged.absolutePath,
        )

        val inspection = MediaArtifactVerifier.verify(merged, selected("public-4k+audio", 3840, 1636))
        assertEquals(3840, inspection.width)
        assertEquals(1636, inspection.height)
        assertTrue(inspection.hasVideo && inspection.hasAudio)
        directory.deleteRecursively()
    }

    @Test
    fun compatibleCopyPreservesOriginalAndResolution() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FFmpeg.getInstance().init(context)
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val directory = File(context.cacheDir, "compatible-copy-${System.nanoTime()}").apply { mkdirs() }
        val original = File(directory, "original.mkv")
        execute(context, ffmpeg, "-f", "lavfi", "-i", "color=size=854x480:rate=2:duration=2", "-f", "lavfi", "-i", "sine=frequency=550:duration=2", "-c:v", "mpeg4", "-c:a", "aac", "-shortest", original.absolutePath)
        val hashBefore = sha256(original)

        val result = CompatibleCopyProcessor(context).convert(Uri.fromFile(original), File(directory, "work"), true, true)

        assertTrue(original.exists())
        assertEquals(hashBefore, sha256(original))
        assertEquals(hashBefore, result.originalSha256)
        assertEquals(854, result.outputInspection.width)
        assertEquals(480, result.outputInspection.height)
        assertTrue(result.outputInspection.hasVideo && result.outputInspection.hasAudio)
        directory.deleteRecursively()
    }

    @Test
    fun finalizesInterruptedLiveTransportStreamWithoutReencoding() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FFmpeg.getInstance().init(context)
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val directory = File(context.cacheDir, "live-finalizer-${System.nanoTime()}").apply { mkdirs() }
        val partial = File(directory, "live.ts.part")
        execute(context, ffmpeg, "-f", "lavfi", "-i", "color=size=640x360:rate=2:duration=2", "-f", "lavfi", "-i", "sine=frequency=440:duration=2", "-c:v", "mpeg2video", "-c:a", "mp2", "-f", "mpegts", partial.absolutePath)

        val result = LiveStreamFinalizer(context).finalize("live", directory, "mkv")
        val inspection = MediaArtifactVerifier.verify(File(result.path), selected("live", 640, 360))

        assertTrue(inspection.hasVideo && inspection.hasAudio)
        directory.listFiles().orEmpty().forEach { it.delete() }
        directory.delete()
    }

    @Test
    fun verifies1080pAndLosslesslyMerged4kArtifactsContainAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FFmpeg.getInstance().init(context)
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val directory = File(context.cacheDir, "artifact-verifier-${System.nanoTime()}").apply { mkdirs() }
        val fullHd = File(directory, "1080p.mp4")
        execute(context, ffmpeg, "-f", "lavfi", "-i", "color=size=1920x1080:rate=1:duration=1", "-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-c:v", "mpeg4", "-q:v", "20", "-c:a", "aac", "-shortest", fullHd.absolutePath)
        val fullHdInspection = MediaArtifactVerifier.verify(fullHd, selected("1080+a", 1920, 1080))
        assertTrue(fullHdInspection.hasVideo && fullHdInspection.hasAudio)

        val video4k = File(directory, "4k-video.mp4")
        val audio4k = File(directory, "4k-audio.m4a")
        val merged4k = File(directory, "4k-merged.mp4")
        execute(context, ffmpeg, "-f", "lavfi", "-i", "color=size=3840x2160:rate=1:duration=1", "-an", "-c:v", "mpeg4", "-q:v", "25", video4k.absolutePath)
        execute(context, ffmpeg, "-f", "lavfi", "-i", "sine=frequency=880:duration=1", "-vn", "-c:a", "aac", audio4k.absolutePath)
        execute(context, ffmpeg, "-i", video4k.absolutePath, "-i", audio4k.absolutePath, "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-shortest", merged4k.absolutePath)
        val inspection4k = MediaArtifactVerifier.verify(merged4k, selected("4k+audio", 3840, 2160))
        assertTrue(inspection4k.hasVideo && inspection4k.hasAudio)

        directory.listFiles().orEmpty().forEach { it.delete() }
        directory.delete()
    }

    private fun selected(id: String, width: Int, height: Int) = SelectedFormat(
        jobId = "test",
        formatId = id,
        width = width,
        height = height,
        hasVideo = true,
        hasAudio = true,
    )

    private fun execute(context: Context, ffmpeg: File, vararg arguments: String) {
        val process = ProcessBuilder(listOf(ffmpeg.absolutePath, "-y", "-hide_banner", "-loglevel", "error") + arguments)
            .redirectErrorStream(true)
            .apply {
                environment()["LD_LIBRARY_PATH"] = listOfNotNull(
                    context.applicationInfo.nativeLibraryDir,
                    File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").absolutePath,
                ).joinToString(":")
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "FFmpeg fixture command failed: $output" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
