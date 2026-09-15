package com.clickdownloader.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

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
    fun validUrl_createsVisibleFoundationJob() {
        val ready = rule.activity.getString(R.string.job_ready)

        rule.onNodeWithText(rule.activity.getString(R.string.url_label))
            .performTextInput("https://example.com/media")
        rule.onNodeWithText(rule.activity.getString(R.string.analyze)).performClick()

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(ready).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(ready).assertIsDisplayed()
    }
}
