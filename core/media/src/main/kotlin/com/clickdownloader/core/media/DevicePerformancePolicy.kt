package com.clickdownloader.core.media

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

data class DevicePerformancePolicy(
    val fragmentConcurrency: Int,
    val lowRam: Boolean,
    val thermallyThrottled: Boolean,
) {
    companion object {
        fun detect(context: Context): DevicePerformancePolicy {
            val activity = context.getSystemService(ActivityManager::class.java)
            val power = context.getSystemService(PowerManager::class.java)
            val hot = Build.VERSION.SDK_INT >= 29 && power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
            val low = activity.isLowRamDevice || activity.memoryClass <= 192
            val concurrency = when {
                hot -> 1
                low -> 2
                activity.memoryClass >= 512 -> 6
                else -> 4
            }
            return DevicePerformancePolicy(concurrency, low, hot)
        }
    }
}
