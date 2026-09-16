package com.clickdownloader.app.bubble

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class BubbleAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        sendBroadcast(
            Intent(BubbleOverlayService.ACTION_FOREGROUND_PACKAGE)
                .setPackage(packageName)
                .putExtra(BubbleOverlayService.EXTRA_FOREGROUND_PACKAGE, event.packageName?.toString()),
        )
    }

    override fun onInterrupt() = Unit
}
