package com.clickdownloader.app

import android.app.Application
import com.clickdownloader.app.download.DownloadNotifications
import com.clickdownloader.app.download.RecoveryWorker

class ClickDownloaderApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.createChannel(this)
        RecoveryWorker.schedule(this)
    }
}
