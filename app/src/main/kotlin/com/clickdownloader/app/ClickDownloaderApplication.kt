package com.clickdownloader.app

import android.app.Application
import com.clickdownloader.app.download.DownloadNotifications
import com.clickdownloader.app.download.RecoveryWorker
import com.clickdownloader.core.browser.SessionCookieExporter

class ClickDownloaderApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        SessionCookieExporter(this).clearAll()
        DownloadNotifications.createChannel(this)
        RecoveryWorker.schedule(this)
    }
}
