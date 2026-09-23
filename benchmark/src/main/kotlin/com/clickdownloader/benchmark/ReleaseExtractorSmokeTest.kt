package com.clickdownloader.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ReleaseExtractorSmokeTest {
    @Test
    fun minifiedReleaseAnalyzesYouTubePage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("pm clear $PACKAGE")
        instrumentation.context.startActivity(
            Intent().apply {
                component = ComponentName(PACKAGE, "$PACKAGE.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("incoming_url", "https://youtu.be/k7wyo43DMGI")
                putExtra("auto_analyze", true)
            },
        )

        assertTrue(
            "Minified release did not expose extracted source formats",
            device.wait(Until.hasObject(By.textContains("source formats")), 60_000),
        )
    }

    private companion object { const val PACKAGE = "com.clickdownloader.app" }
}
