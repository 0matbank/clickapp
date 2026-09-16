package com.clickdownloader.core.extractor

import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.DownloadRequestRepository
import com.clickdownloader.core.domain.ExtractionRepository
import com.clickdownloader.core.domain.PlaylistRepository
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.core.model.DownloadKind
import com.clickdownloader.core.model.DownloadRequest
import com.clickdownloader.core.model.MediaAnalysis
import com.clickdownloader.core.model.MediaFormatOption
import com.clickdownloader.core.model.MediaMetadata
import com.clickdownloader.core.model.SelectedFormat
import com.clickdownloader.core.model.StreamProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue

class QueueExactFormatUseCaseTest {
    @Test
    fun `batch queue keeps a successful item when another item fails`() = runBlocking {
        val jobs = FakeJobs()
        val requests = FakeRequests()
        val extraction = FakeExtraction()
        val playlist = FakePlaylist()
        val format = MediaFormatOption("ok", extension = "mp4", directUrl = "https://cdn/ok", hasVideo = true, hasAudio = true)
        val metadata = MediaMetadata("one", "One", canonicalUrl = "https://source/one")
        val validPending = PendingMediaSelection("one", MediaAnalysis(metadata, listOf(format), webpageUrl = metadata.canonicalUrl))
        jobs.values["one"] = DownloadJob("one", metadata.canonicalUrl, metadata.title, DownloadJobState.WAITING_FOR_SELECTION, 1, 1, appVersion = "test")
        val invalidPending = PendingMediaSelection("two", MediaAnalysis(metadata.copy(jobId = "two"), emptyList(), webpageUrl = "https://source/two"))
        val batch = BatchPreparation("playlist", listOf(
            PreparedBatchItem("item-one", validPending, format, null),
            PreparedBatchItem("item-two", invalidPending, format, null),
        ), emptyList())

        jobs.values["playlist"] = DownloadJob("playlist", "https://source/list", "List", DownloadJobState.WAITING_FOR_SELECTION, 1, 1, appVersion = "test")
        val (queued, failed) = ConfirmPlaylistBatchUseCase(QueueExactFormatUseCase(jobs, requests, extraction), playlist, jobs)(batch)

        assertEquals(listOf("one"), queued)
        assertEquals(listOf("item-two"), failed)
        assertEquals(mapOf("item-one" to "one"), playlist.attachments)
        assertTrue(requests.values.containsKey("one"))
        assertEquals(DownloadJobState.PLAYLIST_QUEUED, jobs.values["playlist"]!!.state)
    }

    @Test
    fun `explicit video and audio IDs are persisted without fallback`() = runBlocking {
        val jobs = FakeJobs()
        val requests = FakeRequests()
        val extraction = FakeExtraction()
        val video = MediaFormatOption("401", extension = "mp4", protocol = StreamProtocol.DASH, manifestUrl = "https://cdn/v.mpd", height = 2160, videoCodec = "av01", hasVideo = true, hasAudio = false)
        val audio = MediaFormatOption("251", extension = "webm", protocol = StreamProtocol.HTTP, directUrl = "https://cdn/a", audioCodec = "opus", hasVideo = false, hasAudio = true)
        val metadata = MediaMetadata("job", "Exact source", canonicalUrl = "https://source/watch")
        jobs.value = DownloadJob("job", metadata.canonicalUrl, metadata.title, DownloadJobState.WAITING_FOR_SELECTION, 1, 1, appVersion = "test")
        val pending = PendingMediaSelection("job", MediaAnalysis(metadata, listOf(video, audio), webpageUrl = metadata.canonicalUrl))

        QueueExactFormatUseCase(jobs, requests, extraction)(pending, video, audio)

        assertEquals("401+251", extraction.selected!!.formatId)
        assertEquals("https://cdn/v.mpd", requests.value!!.url)
        assertEquals("https://cdn/a", requests.value!!.secondaryUrl)
        assertEquals(DownloadKind.DASH, requests.value!!.kind)
        assertEquals(DownloadJobState.QUEUED, jobs.value!!.state)
    }

    private class FakeJobs : DownloadJobRepository {
        var value: DownloadJob? = null
        val values = mutableMapOf<String, DownloadJob>()
        override fun observeJobs(): Flow<List<DownloadJob>> = flowOf(values.values.toList() + listOfNotNull(value))
        override suspend fun upsert(job: DownloadJob) { value = job; values[job.id] = job }
        override suspend fun findById(id: String) = values[id] ?: value?.takeIf { it.id == id }
        override suspend fun updateState(id: String, state: DownloadJobState, errorCode: String?, errorMessage: String?) {
            val updated = findById(id)!!.copy(state = state)
            value = updated
            values[id] = updated
        }
        override suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long?) = Unit
        override suspend fun recoverInterruptedJobs() = Unit
    }

    private class FakeRequests : DownloadRequestRepository {
        var value: DownloadRequest? = null
        val values = mutableMapOf<String, DownloadRequest>()
        override fun observeRequests(): Flow<List<DownloadRequest>> = flowOf(values.values.toList())
        override suspend fun upsert(request: DownloadRequest) { value = request; values[request.jobId] = request }
        override suspend fun findByJobId(jobId: String) = values[jobId]
        override suspend fun nextQueued() = value
        override suspend fun delete(jobId: String) { value = null }
    }

    private class FakePlaylist : PlaylistRepository {
        val attachments = mutableMapOf<String, String>()
        override suspend fun savePlaylist(id: String, sourceUrl: String, title: String, items: List<com.clickdownloader.core.model.PlaylistItem>) = Unit
        override suspend fun attachJob(playlistId: String, itemId: String, jobId: String) { attachments[itemId] = jobId }
    }

    private class FakeExtraction : ExtractionRepository {
        var selected: SelectedFormat? = null
        override suspend fun saveMetadata(metadata: MediaMetadata) = Unit
        override suspend fun saveSelectedFormat(format: SelectedFormat) { selected = format }
        override suspend fun findSelectedFormat(jobId: String) = selected
    }
}
