package com.clickdownloader.core.extractor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ExtractorUpdateChannel { STABLE, BETA }

data class ExtractorUpdateResult(val changed: Boolean, val version: String, val rolledBack: Boolean = false)

class ExtractorUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.noBackupFilesDir, "youtubedl-android")
    private val engine = File(root, YoutubeDL.ytdlpDirName)
    private val backupRoot = File(appContext.noBackupFilesDir, "extractor-known-good")
    private val backupEngine = File(backupRoot, "engine")
    private val manifest = File(backupRoot, "manifest.txt")

    suspend fun update(channel: ExtractorUpdateChannel): ExtractorUpdateResult = withContext(Dispatchers.IO) {
        ensureInitialized()
        val beforeVersion = YoutubeDL.versionName(appContext).orEmpty()
        require(engine.exists()) { "Bundled extractor is unavailable" }
        val stagedBackup = File(appContext.noBackupFilesDir, "extractor-backup-staging").apply { deleteRecursively() }
        require(engine.copyRecursively(stagedBackup, overwrite = true)) { "Could not stage the current extractor" }
        val backupHash = treeHash(stagedBackup)
        try {
            val updateChannel = when (channel) {
                ExtractorUpdateChannel.STABLE -> YoutubeDL.UpdateChannel._STABLE
                ExtractorUpdateChannel.BETA -> YoutubeDL.UpdateChannel._NIGHTLY
            }
            val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, updateChannel)
            val afterVersion = YoutubeDL.versionName(appContext).orEmpty()
            require(afterVersion.isNotBlank() && engine.exists() && treeHash(engine).isNotBlank()) { "Updated extractor failed verification" }
            if (status == YoutubeDL.UpdateStatus.DONE) activateBackup(stagedBackup, beforeVersion, backupHash, channel)
            else stagedBackup.deleteRecursively()
            ExtractorUpdateResult(status == YoutubeDL.UpdateStatus.DONE, afterVersion)
        } catch (error: Throwable) {
            restoreDirectory(stagedBackup, engine)
            throw IllegalStateException("Extractor update failed; the previous engine was restored", error)
        }
    }

    suspend fun rollback(): ExtractorUpdateResult = withContext(Dispatchers.IO) {
        ensureInitialized()
        require(backupEngine.isDirectory && manifest.isFile) { "No verified extractor backup is available" }
        val fields = manifest.readLines().associate { line -> line.substringBefore('=') to line.substringAfter('=', "") }
        val payload = "${fields["version"]}|${fields["sha256"]}|${fields["channel"]}"
        require(constantTimeEquals(fields["signature"].orEmpty(), sign(payload))) { "Extractor backup manifest signature is invalid" }
        require(fields["sha256"] == treeHash(backupEngine)) { "Extractor backup checksum is invalid" }
        val currentStaging = File(appContext.noBackupFilesDir, "extractor-current-staging").apply { deleteRecursively() }
        engine.copyRecursively(currentStaging, overwrite = true)
        val currentVersion = YoutubeDL.versionName(appContext).orEmpty()
        val currentHash = treeHash(currentStaging)
        restoreDirectory(backupEngine, engine)
        val restoredVersion = fields["version"].orEmpty()
        activateBackup(currentStaging, currentVersion, currentHash, ExtractorUpdateChannel.STABLE)
        ExtractorUpdateResult(changed = true, version = restoredVersion, rolledBack = true)
    }

    fun currentVersion(): String? = runCatching {
        ensureInitialized()
        YoutubeDL.versionName(appContext)
    }.getOrNull()

    @Synchronized
    private fun ensureInitialized() = YoutubeDL.getInstance().init(appContext)

    private fun activateBackup(staged: File, version: String, hash: String, channel: ExtractorUpdateChannel) {
        backupRoot.mkdirs()
        backupEngine.deleteRecursively()
        require(staged.renameTo(backupEngine) || staged.copyRecursively(backupEngine, overwrite = true)) { "Could not preserve extractor rollback copy" }
        staged.deleteRecursively()
        val payload = "$version|$hash|${channel.name}"
        val temporary = File(backupRoot, "manifest.tmp")
        temporary.writeText("version=$version\nsha256=$hash\nchannel=${channel.name}\nsignature=${sign(payload)}\n")
        require(temporary.renameTo(manifest) || temporary.copyTo(manifest, overwrite = true).exists()) { "Could not activate extractor backup manifest" }
        temporary.delete()
    }

    private fun restoreDirectory(source: File, destination: File) {
        require(source.isDirectory) { "Extractor rollback source is missing" }
        val staging = File(destination.parentFile, "${destination.name}.restore").apply { deleteRecursively() }
        require(source.copyRecursively(staging, overwrite = true)) { "Could not copy extractor rollback source" }
        destination.deleteRecursively()
        require(staging.renameTo(destination) || staging.copyRecursively(destination, overwrite = true)) { "Could not restore extractor" }
        staging.deleteRecursively()
    }

    private fun treeHash(directory: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        directory.walkTopDown().filter(File::isFile).sortedBy { it.relativeTo(directory).invariantSeparatorsPath }.forEach { file ->
            digest.update(file.relativeTo(directory).invariantSeparatorsPath.toByteArray())
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(signingKey())
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun signingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).build())
        return generator.generateKey()
    }

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(left.toByteArray(), right.toByteArray())

    private companion object { const val KEY_ALIAS = "click_downloader_extractor_manifest_hmac_v1" }
}
