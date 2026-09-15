package com.clickdownloader.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.clickdownloader.core.domain.SettingsRepository
import com.clickdownloader.core.model.AppLanguage
import com.clickdownloader.core.model.AppSettings
import com.clickdownloader.core.model.AppThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    context: Context,
) : SettingsRepository {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile(FILE_NAME)
    }

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                language = preferences[Keys.LANGUAGE]
                    ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                    ?: AppLanguage.SYSTEM,
                themeMode = preferences[Keys.THEME]
                    ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                    ?: AppThemeMode.SYSTEM,
                askQualityEveryTime = preferences[Keys.ASK_QUALITY] ?: true,
                downloadDirectoryUri = preferences[Keys.DOWNLOAD_DIRECTORY],
            )
        }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    override suspend fun setThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { it[Keys.THEME] = themeMode.name }
    }

    override suspend fun setAskQualityEveryTime(enabled: Boolean) {
        dataStore.edit { it[Keys.ASK_QUALITY] = enabled }
    }

    override suspend fun setDownloadDirectoryUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri == null) preferences.remove(Keys.DOWNLOAD_DIRECTORY)
            else preferences[Keys.DOWNLOAD_DIRECTORY] = uri
        }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val ASK_QUALITY = booleanPreferencesKey("ask_quality_every_time")
        val DOWNLOAD_DIRECTORY = stringPreferencesKey("download_directory_uri")
    }

    private companion object {
        const val FILE_NAME = "click_downloader_settings.preferences_pb"
    }
}

