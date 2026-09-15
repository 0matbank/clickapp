package com.clickdownloader.app

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAnalyzeActivityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun sharedTextShowsEveryDistinctUrlForExplicitSelection() {
        val first = "https://example.test/one.mp4"
        val second = "https://example.test/two.mp4"
        val intent = Intent(ApplicationProvider.getApplicationContext(), QuickAnalyzeActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Choose $first or $second and do not duplicate $first")

        ActivityScenario.launch<QuickAnalyzeActivity>(intent).use {
            rule.onNodeWithText(first).assertIsDisplayed()
            rule.onNodeWithText(second).assertIsDisplayed()
        }
    }
}
