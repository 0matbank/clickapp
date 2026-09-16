package com.clickdownloader.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
                bubbleEnabled = preferences[Keys.BUBBLE_ENABLED] ?: false,
                bubbleOpacity = (preferences[Keys.BUBBLE_OPACITY] ?: 0.9f).coerceIn(0.35f, 1f),
                bubbleSizeDp = (preferences[Keys.BUBBLE_SIZE] ?: 56).coerceIn(40, 80),
                bubbleAllowlistedPackages = preferences[Keys.BUBBLE_ALLOWLIST].orEmpty(),
                accessibilityBubbleAssist = preferences[Keys.BUBBLE_ACCESSIBILITY] ?: false,
                allowConversionOnLowBattery = preferences[Keys.CONVERT_LOW_BATTERY] ?: false,
                allowConversionWhenHot = preferences[Keys.CONVERT_WHEN_HOT] ?: false,
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

    override suspend fun setBubbleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BUBBLE_ENABLED] = enabled }
    }

    override suspend fun setBubbleOpacity(opacity: Float) {
        dataStore.edit { it[Keys.BUBBLE_OPACITY] = opacity.coerceIn(0.35f, 1f) }
    }

    override suspend fun setBubbleSizeDp(sizeDp: Int) {
        dataStore.edit { it[Keys.BUBBLE_SIZE] = sizeDp.coerceIn(40, 80) }
    }

    override suspend fun setBubbleAllowlistedPackages(packages: Set<String>) {
        dataStore.edit { it[Keys.BUBBLE_ALLOWLIST] = packages.map(String::trim).filter(PACKAGE_PATTERN::matches).toSet() }
    }

    override suspend fun setAccessibilityBubbleAssist(enabled: Boolean) {
        dataStore.edit { it[Keys.BUBBLE_ACCESSIBILITY] = enabled }
    }

    override suspend fun setAllowConversionOnLowBattery(enabled: Boolean) {
        dataStore.edit { it[Keys.CONVERT_LOW_BATTERY] = enabled }
    }

    override suspend fun setAllowConversionWhenHot(enabled: Boolean) {
        dataStore.edit { it[Keys.CONVERT_WHEN_HOT] = enabled }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val ASK_QUALITY = booleanPreferencesKey("ask_quality_every_time")
        val DOWNLOAD_DIRECTORY = stringPreferencesKey("download_directory_uri")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        val BUBBLE_SIZE = intPreferencesKey("bubble_size_dp")
        val BUBBLE_ALLOWLIST = stringSetPreferencesKey("bubble_allowlisted_packages")
        val BUBBLE_ACCESSIBILITY = booleanPreferencesKey("bubble_accessibility_assist")
        val CONVERT_LOW_BATTERY = booleanPreferencesKey("allow_conversion_low_battery")
        val CONVERT_WHEN_HOT = booleanPreferencesKey("allow_conversion_when_hot")
    }

    private companion object {
        const val FILE_NAME = "click_downloader_settings.preferences_pb"
        val PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}
