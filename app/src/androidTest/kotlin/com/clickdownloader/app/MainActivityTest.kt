package com.clickdownloader.app

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    private lateinit var server: MockWebServer

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun bottomNavigation_reachesEveryFoundationDestination() {
        val downloads = rule.activity.getString(R.string.nav_downloads)
        val library = rule.activity.getString(R.string.nav_library)
        val settings = rule.activity.getString(R.string.nav_settings)

        rule.onNodeWithText(downloads).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.tab_active)).assertIsDisplayed()
        rule.onNodeWithText(library).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.library_empty)).assertIsDisplayed()
        rule.onNodeWithText(settings).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.settings_language)).assertIsDisplayed()
    }

    @Test
    fun directMediaUrl_isAnalyzedQueuedAndShown() {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Type", "video/mp4")
                .addHeader("Content-Range", "bytes 0-0/11")
                .setBody("x"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "video/mp4")
                .setBody("hello-video"),
        )
        val mediaName = "sample.mp4"

        rule.onNodeWithText(rule.activity.getString(R.string.url_label))
            .performTextInput(server.url("/$mediaName").toString())
        rule.onNodeWithText(rule.activity.getString(R.string.analyze)).performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(mediaName).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(mediaName).assertIsDisplayed()
    }
}
