package com.clickdownloader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisFailureClassifierTest {
    @Test fun unavailableSourceIsExplained() {
        assertEquals(
            UiMessage.SOURCE_UNAVAILABLE,
            classifyAnalysisFailure(IllegalStateException("ERROR: This video is unavailable")),
        )
    }

    @Test fun loginRequirementUsesSessionGuidance() {
        assertEquals(
            UiMessage.SESSION_REQUIRED,
            classifyAnalysisFailure(IllegalStateException("Sign in or provide cookies")),
        )
    }

    @Test fun unknownFailureUsesGenericRecoveryGuidance() {
        assertEquals(UiMessage.ANALYZE_FAILED, classifyAnalysisFailure(IllegalStateException("network timeout")))
    }
}
