package com.clickdownloader.core.browser

import android.content.Context
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicInteger

object BrowserLifecycleProbe {
    val activeWebViews = AtomicInteger(0)
}

class BrowserWebViewHandle(context: Context) {
    val webView = WebView(context)
    private var released = false

    init { BrowserLifecycleProbe.activeWebViews.incrementAndGet() }

    fun release() {
        if (released) return
        released = true
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
        BrowserLifecycleProbe.activeWebViews.decrementAndGet()
    }
}
