package com.clickdownloader.core.domain

import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import java.net.URI
import java.time.Clock
import java.util.UUID

class CreateLocalJobUseCase(
    private val repository: DownloadJobRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(rawUrl: String, appVersion: String): Result<DownloadJob> = runCatching {
        val normalized = normalizeHttpUrl(rawUrl)
        val now = clock.millis()
        val job = DownloadJob(
            id = idFactory(),
            sourceUrl = normalized.toASCIIString(),
            displayTitle = normalized.host.removePrefix("www."),
            state = DownloadJobState.CREATED,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            appVersion = appVersion,
        )
        repository.upsert(job)
        job
    }

    private fun normalizeHttpUrl(value: String): URI {
        val uri = URI(value.trim()).normalize()
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
        require(!uri.host.isNullOrBlank())
        require(uri.userInfo == null) { "URLs containing credentials are not accepted" }
        return uri
    }
}

