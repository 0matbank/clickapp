package com.clickdownloader.core.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionPrivacyTest {
    @Test
    fun cookiesAreEncryptedExcludedFromBackupAndClearable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = SessionVault(context)
        val host = "privacy-${System.nanoTime()}.test"
        val cookie = "SID=private-cookie-value; mode=member"

        vault.save(host, cookie)

        assertEquals(cookie, vault.restore(host))
        assertTrue(vault.encryptedFile(host).canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath))
        assertFalse(vault.encryptedFile(host).readBytes().toString(Charsets.ISO_8859_1).contains("private-cookie-value"))
        vault.clear(host)
        assertNull(vault.restore(host))
    }

    @Test
    fun exportedCookieFileIsPrivateAndTemporary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = SessionCookieExporter(context).export("example.test", "SID=value")
        assertTrue(file.canonicalPath.startsWith(context.cacheDir.canonicalPath))
        assertTrue(file.canRead() && file.canWrite())
        file.delete()
    }
}
