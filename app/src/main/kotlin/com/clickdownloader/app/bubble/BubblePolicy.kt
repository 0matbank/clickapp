package com.clickdownloader.app.bubble

internal object BubblePolicy {
    private val urlPattern = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)

    fun firstSupportedUrl(text: CharSequence?): String? = text?.let { value ->
        urlPattern.find(value)?.value?.trimEnd('.', ',', ')', ']', '}')
    }

    fun shouldShowForPackage(
        accessibilityAssist: Boolean,
        allowlist: Set<String>,
        foregroundPackage: String?,
        ownPackage: String,
    ): Boolean {
        if (foregroundPackage == ownPackage) return false
        if (!accessibilityAssist || allowlist.isEmpty()) return true
        return foregroundPackage != null && foregroundPackage in allowlist
    }
}
