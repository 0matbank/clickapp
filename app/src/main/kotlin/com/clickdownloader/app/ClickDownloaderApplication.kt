package com.clickdownloader.app

import android.app.Application
import android.app.ActivityManager
import android.os.Build
import com.clickdownloader.app.download.DownloadNotifications
import com.clickdownloader.app.download.RecoveryWorker
import com.clickdownloader.core.browser.SessionCookieExporter

class ClickDownloaderApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.createChannel(this)
        if (isDefaultProcess()) {
            SessionCookieExporter(this).clearAll()
            RecoveryWorker.schedule(this)
        }
    }

    private fun isDefaultProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= 28) getProcessName() else {
            val pid = android.os.Process.myPid()
            getSystemService(ActivityManager::class.java).runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
        return processName == packageName
    }
}
