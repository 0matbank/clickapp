package com.clickdownloader.core.media

import android.content.Context
import android.os.Process
import android.system.Os
import com.yausername.ffmpeg.FFmpeg
import java.io.File

object FfmpegRuntime {
    @Synchronized
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        FFmpeg.getInstance().init(appContext)
        val libraryDirectory = extractedLibraryDirectory(appContext)
        libraryDirectory.mkdirs()
        ensureSystemAlias(
            alias = File(libraryDirectory, "libexpat.so.1"),
            target = File(if (Process.is64Bit()) "/system/lib64/libexpat.so" else "/system/lib/libexpat.so"),
        )
    }

    fun libraryPath(context: Context): String = listOf(
        context.applicationInfo.nativeLibraryDir,
        extractedLibraryDirectory(context).absolutePath,
    ).joinToString(":")

    private fun extractedLibraryDirectory(context: Context) =
        File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")

    private fun ensureSystemAlias(alias: File, target: File) {
        if (alias.exists() || !target.exists()) return
        runCatching { Os.symlink(target.absolutePath, alias.absolutePath) }
            .getOrElse { error ->
                if (!alias.exists()) throw IllegalStateException("Unable to prepare FFmpeg runtime dependency", error)
            }
    }
}
