package com.clickdownloader.app

import android.app.Application

class ClickDownloaderApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }
}

