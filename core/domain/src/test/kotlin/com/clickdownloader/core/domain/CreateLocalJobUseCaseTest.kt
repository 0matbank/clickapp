package com.clickdownloader.core.domain

import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateLocalJobUseCaseTest {
    private val repository = FakeJobRepository()
    private val useCase = CreateLocalJobUseCase(
        repository = repository,
        clock = Clock.fixed(Instant.ofEpochMilli(42), ZoneOffset.UTC),
        idFactory = { "job-1" },
    )

    @Test
    fun `valid URL creates persistent created state without fake progress`() = runTest {
        val job = useCase(" https://www.example.com/video?id=7 ", "0.1.0").getOrThrow()

        assertEquals("job-1", job.id)
        assertEquals("example.com", job.displayTitle)
        assertEquals(DownloadJobState.CREATED, job.state)
        assertEquals(0, job.downloadedBytes)
        assertEquals(null, job.totalBytes)
        assertEquals(job, repository.findById(job.id))
    }

    @Test
    fun `non web and credential URLs are rejected`() = runTest {
        assertTrue(useCase("file:///tmp/video.mp4", "0.1.0").isFailure)
        assertTrue(useCase("https://user:secret@example.com/video", "0.1.0").isFailure)
    }
}

private class FakeJobRepository : DownloadJobRepository {
    private val jobs = MutableStateFlow<List<DownloadJob>>(emptyList())

    override fun observeJobs(): Flow<List<DownloadJob>> = jobs

    override suspend fun upsert(job: DownloadJob) {
        jobs.value = jobs.value.filterNot { it.id == job.id } + job
    }

    override suspend fun findById(id: String): DownloadJob? = jobs.value.find { it.id == id }
}

