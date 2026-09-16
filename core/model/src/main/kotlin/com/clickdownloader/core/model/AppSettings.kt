package com.clickdownloader.core.model

enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    BANGLA,
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val askQualityEveryTime: Boolean = true,
    val downloadDirectoryUri: String? = null,
    val bubbleEnabled: Boolean = false,
    val bubbleOpacity: Float = 0.9f,
    val bubbleSizeDp: Int = 56,
    val bubbleAllowlistedPackages: Set<String> = emptySet(),
    val accessibilityBubbleAssist: Boolean = false,
)
