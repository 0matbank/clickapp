package com.clickdownloader.app

import android.content.Context
import com.clickdownloader.core.data.database.DataRepositories
import com.clickdownloader.core.data.settings.DataStoreSettingsRepository
import com.clickdownloader.core.domain.DownloadJobRepository
import com.clickdownloader.core.domain.SettingsRepository
import com.clickdownloader.core.domain.StorageGateway
import com.clickdownloader.core.storage.AndroidStorageGateway

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val downloadJobRepository: DownloadJobRepository by lazy {
        DataRepositories.createDownloadJobRepository(appContext)
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext)
    }

    val storageGateway: StorageGateway by lazy {
        AndroidStorageGateway(appContext)
    }
}
