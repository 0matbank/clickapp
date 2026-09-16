package com.clickdownloader.core.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionVault(context: Context) {
    private val directory = File(context.noBackupFilesDir, "browser_sessions").apply { mkdirs() }

    fun save(host: String, cookieHeader: String) {
        require(host.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(host.toByteArray())
        val encrypted = cipher.doFinal(cookieHeader.toByteArray())
        val bytes = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
        fileFor(host).outputStream().use { it.write(bytes) }
    }

    fun restore(host: String): String? = runCatching {
        val bytes = fileFor(host).takeIf(File::isFile)?.readBytes() ?: return null
        val buffer = ByteBuffer.wrap(bytes)
        val iv = ByteArray(buffer.get().toInt() and 0xff).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(host.toByteArray())
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    fun clear(host: String) { fileFor(host).delete() }

    fun clearAll() { directory.listFiles().orEmpty().forEach(File::delete) }

    fun encryptedFile(host: String): File = fileFor(host)

    private fun fileFor(host: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(host.lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.session")
    }

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "click_downloader_browser_session_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
