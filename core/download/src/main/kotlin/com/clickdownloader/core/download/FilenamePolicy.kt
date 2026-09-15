package com.clickdownloader.core.download

object FilenamePolicy {
    private val reserved = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val repeatedWhitespace = Regex("\\s+")

    fun sanitize(input: String, fallback: String = "download", maxLength: Int = 180): String {
        val extension = input.substringAfterLast('.', "").takeIf { it.length in 1..12 }
        val clean = input
            .replace(reserved, "_")
            .replace(repeatedWhitespace, " ")
            .trim(' ', '.')
            .ifBlank { fallback }
        if (clean.length <= maxLength) return clean
        val suffix = extension?.let { ".$it" }.orEmpty()
        return clean.take((maxLength - suffix.length).coerceAtLeast(1)).trimEnd() + suffix
    }

    fun keepBothName(original: String, index: Int): String {
        require(index >= 1)
        val dot = original.lastIndexOf('.')
        return if (dot > 0) "${original.substring(0, dot)} ($index)${original.substring(dot)}" else "$original ($index)"
    }
}
