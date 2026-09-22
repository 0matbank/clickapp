package com.clickdownloader.app.performance

class HeavyWorkCoordinator {
    private var downloads = 0
    private var conversions = 0

    @Synchronized fun tryStartDownload(lowRam: Boolean): Boolean {
        if (lowRam && conversions > 0) return false
        downloads++
        return true
    }
    @Synchronized fun downloadFinished() { downloads = (downloads - 1).coerceAtLeast(0) }

    @Synchronized fun tryStartConversion(lowRam: Boolean): Boolean {
        if (lowRam && downloads > 0) return false
        conversions++
        return true
    }
    @Synchronized fun conversionFinished() { conversions = (conversions - 1).coerceAtLeast(0) }
}
