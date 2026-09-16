package com.clickdownloader.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clickdownloader.core.browser.BrowserLifecycleProbe
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserLifecycleTest {
    @Test
    fun browserIsLazyAndReleasesWebViewOnExit() {
        assertEquals(0, BrowserLifecycleProbe.activeWebViews.get())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scenario = ActivityScenario.launch<BrowserActivity>(
            Intent(context, BrowserActivity::class.java)
                .putExtra(BrowserActivity.EXTRA_URL, "http://127.0.0.1:9/")
                .putExtra(BrowserActivity.EXTRA_INCOGNITO, true),
        )
        scenario.onActivity { assertEquals(1, BrowserLifecycleProbe.activeWebViews.get()) }
        scenario.close()
        assertEquals(0, BrowserLifecycleProbe.activeWebViews.get())
    }
}
