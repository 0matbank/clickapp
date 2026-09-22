package com.clickdownloader.core.download

import com.clickdownloader.core.model.DownloadRequest
import com.clickdownloader.core.model.DirectDownloadException
import com.clickdownloader.core.model.DirectDownloadFailure
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpDirectDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        directory = createTempDirectory("direct-download-").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test
    fun `range response resumes exact partial without changing bytes`() = runTest {
        val media = ByteArray(32_768) { (it % 251).toByte() }
        val partial = File(directory, "media.part").apply { writeBytes(media.copyOfRange(0, 4_096)) }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes 4096-32767/32768")
                .setBody(Buffer().write(media, 4_096, media.size - 4_096)),
        )
        val request = DownloadRequest(
            jobId = "job",
            url = server.url("/media.mp4").toString(),
            displayName = "media.mp4",
            mimeType = "video/mp4",
            expectedBytes = media.size.toLong(),
        )

        val result = HttpDirectDownloader(OkHttpClient()).download(request, partial)

        assertTrue(result.resumed)
        assertArrayEquals(media, partial.readBytes())
        assertEquals("bytes=4096-", server.takeRequest().headers["Range"])
    }

    @Test
    fun `server ignoring range restarts rather than corrupting output`() = runTest {
        val media = "complete-media-content".encodeToByteArray()
        val partial = File(directory, "media.part").apply { writeText("old-partial") }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(media)))
        val request = DownloadRequest("job", server.url("/media.mp4").toString(), "media.mp4", "video/mp4", expectedBytes = media.size.toLong())

        val result = HttpDirectDownloader(OkHttpClient()).download(request, partial)

        assertTrue(result.serverIgnoredRange)
        assertArrayEquals(media, partial.readBytes())
    }

    @Test
    fun `network interruption preserves partial and next attempt resumes byte exactly`() = runTest {
        val media = ByteArray(256_000) { (it % 239).toByte() }
        val partial = File(directory, "interrupted.part")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(media))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val request = DownloadRequest(
            jobId = "interrupted-job",
            url = server.url("/large.mp4").toString(),
            displayName = "large.mp4",
            mimeType = "video/mp4",
            expectedBytes = media.size.toLong(),
        )

        runCatching { HttpDirectDownloader(OkHttpClient()).download(request, partial) }
        val retainedBytes = partial.length().toInt()
        assertTrue("A useful partial file must survive interruption", retainedBytes in 1 until media.size)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes $retainedBytes-${media.lastIndex}/${media.size}")
                .setBody(Buffer().write(media, retainedBytes, media.size - retainedBytes)),
        )

        val result = HttpDirectDownloader(OkHttpClient()).download(request, partial)

        assertTrue(result.resumed)
        assertArrayEquals(media, partial.readBytes())
        server.takeRequest()
        assertEquals("bytes=$retainedBytes-", server.takeRequest().headers["Range"])
    }

    @Test fun `expired rate limited and server failures have recoverable classifications`() = runTest {
        val cases = listOf(
            403 to DirectDownloadFailure.LINK_EXPIRED,
            410 to DirectDownloadFailure.LINK_EXPIRED,
            429 to DirectDownloadFailure.RATE_LIMITED,
            503 to DirectDownloadFailure.NETWORK,
        )
        cases.forEachIndexed { index, (status, expected) ->
            server.enqueue(MockResponse().setResponseCode(status))
            val request = DownloadRequest("job-$index", server.url("/media-$index.mp4").toString(), "media.mp4", "video/mp4")
            val failure = try {
                HttpDirectDownloader(OkHttpClient()).download(request, File(directory, "$index.part"))
                throw AssertionError("Expected HTTP $status to fail")
            } catch (error: DirectDownloadException) { error }
            assertEquals(expected, failure.failure)
        }
    }
}
