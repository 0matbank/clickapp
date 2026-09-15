package com.clickdownloader.core.model

enum class DownloadJobState {
    CREATED,
    ANALYZING,
    WAITING_FOR_SELECTION,
    QUEUED,
    PREPARING,
    DOWNLOADING_VIDEO,
    DOWNLOADING_AUDIO,
    DOWNLOADING_FRAGMENTS,
    MERGING,
    OPTIONAL_CONVERSION,
    VERIFYING,
    COMPLETED,
    PAUSED,
    WAITING_FOR_NETWORK,
    AUTH_REQUIRED,
    LINK_EXPIRED,
    STORAGE_REQUIRED,
    RETRY_SCHEDULED,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    fun canTransitionTo(next: DownloadJobState): Boolean {
        if (next == this) return true
        if (isTerminal) return false
        if (next == CANCELLED || next == FAILED) return true
        if (next in recoverableInterruptions) return this in interruptibleStates

        return when (this) {
            CREATED -> next == ANALYZING
            ANALYZING -> next == WAITING_FOR_SELECTION
            WAITING_FOR_SELECTION -> next == QUEUED
            QUEUED -> next == PREPARING
            PREPARING -> next in downloadStates
            DOWNLOADING_VIDEO,
            DOWNLOADING_AUDIO,
            DOWNLOADING_FRAGMENTS,
            -> next in downloadStates || next == MERGING || next == OPTIONAL_CONVERSION || next == VERIFYING
            MERGING -> next == OPTIONAL_CONVERSION || next == VERIFYING
            OPTIONAL_CONVERSION -> next == VERIFYING
            VERIFYING -> next == COMPLETED
            PAUSED,
            WAITING_FOR_NETWORK,
            AUTH_REQUIRED,
            LINK_EXPIRED,
            STORAGE_REQUIRED,
            RETRY_SCHEDULED,
            FAILED,
            -> next == QUEUED || next == PREPARING || next == ANALYZING
            COMPLETED,
            CANCELLED,
            -> false
        }
    }

    companion object {
        private val downloadStates = setOf(
            DOWNLOADING_VIDEO,
            DOWNLOADING_AUDIO,
            DOWNLOADING_FRAGMENTS,
        )
        private val recoverableInterruptions = setOf(
            PAUSED,
            WAITING_FOR_NETWORK,
            AUTH_REQUIRED,
            LINK_EXPIRED,
            STORAGE_REQUIRED,
            RETRY_SCHEDULED,
        )
        private val interruptibleStates = setOf(
            ANALYZING,
            WAITING_FOR_SELECTION,
            QUEUED,
            PREPARING,
            DOWNLOADING_VIDEO,
            DOWNLOADING_AUDIO,
            DOWNLOADING_FRAGMENTS,
            MERGING,
            OPTIONAL_CONVERSION,
            VERIFYING,
        )
    }
}

