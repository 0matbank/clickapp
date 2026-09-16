package com.clickdownloader.core.media

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpMediaCommandTest {
    @Test
    fun `adaptive command preserves exact IDs and enables resumable lossless merge`() {
        val directory = createTempDirectory("adaptive-command").toFile()
        val command = YtDlpMediaCommand.build(
            sourceUrl = "https://source.test/watch",
            exactFormatSpec = "401+251",
            workingDirectory = directory,
            jobId = "job",
            preferredContainer = "mkv",
            cookieFilePath = "/private/session-cookie.txt",
        ).buildCommand()

        assertEquals("401+251", command[command.indexOf("--format") + 1])
        assertEquals("mkv", command[command.indexOf("--merge-output-format") + 1])
        assertTrue("--continue" in command)
        assertTrue("--keep-fragments" in command)
        assertTrue("--embed-metadata" in command)
        assertTrue("--embed-thumbnail" in command)
        assertTrue("--embed-subs" in command)
        assertEquals("/private/session-cookie.txt", command[command.indexOf("--cookies") + 1])
        assertFalse(command.any { it.contains("/") && it.contains("best") })
        directory.deleteRecursively()
    }

    @Test
    fun `fragment scanner creates durable partial and complete checkpoints`() {
        val directory = createTempDirectory("fragment-checkpoints").toFile()
        File(directory, "stream.part-Frag12.part").writeBytes(ByteArray(7))
        File(directory, "stream.part-Frag13").writeBytes(ByteArray(11))

        val checkpoints = FragmentCheckpointScanner.scan("job", "401+251", directory).sortedBy { it.fragmentIndex }

        assertEquals(listOf(12L, 13L), checkpoints.map { it.fragmentIndex })
        assertEquals(listOf(7L, 11L), checkpoints.map { it.downloadedBytes })
        assertFalse(checkpoints.first().completed)
        assertTrue(checkpoints.last().completed)
        directory.listFiles().orEmpty().forEach { it.delete() }
        directory.delete()
    }
}
