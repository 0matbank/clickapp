package com.clickdownloader.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clickdownloader.core.browser.BrowserWebViewHandle
import com.clickdownloader.core.browser.DetectedMedia
import com.clickdownloader.core.browser.MediaRequestDetector
import com.clickdownloader.core.browser.SessionVault
import java.net.URI
import java.net.URLEncoder

class BrowserActivity : AppCompatActivity() {
    private lateinit var handle: BrowserWebViewHandle
    private lateinit var address: EditText
    private lateinit var detectedAdapter: ArrayAdapter<String>
    private lateinit var useSession: CheckBox
    private val detected = linkedMapOf<String, DetectedMedia>()
    private val vault by lazy { SessionVault(this) }
    private val cookies = CookieManager.getInstance()
    private val incognito by lazy { intent.getBooleanExtra(EXTRA_INCOGNITO, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (incognito) clearRuntimeSession()
        handle = BrowserWebViewHandle(this)
        setContentView(buildUi())
        configureWebView()
        loadAddress(intent.getStringExtra(EXTRA_URL) ?: HOME_URL)
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        address = EditText(this).apply {
            hint = getString(R.string.browser_address_hint)
            setSingleLine(true)
            setOnEditorActionListener { _, _, _ -> loadAddress(text.toString()); true }
        }
        root.addView(address, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(buttonRow(
            button("←") { handle.webView.takeIf(WebView::canGoBack)?.goBack() },
            button("→") { handle.webView.takeIf(WebView::canGoForward)?.goForward() },
            button("↻") { handle.webView.reload() },
            button(getString(R.string.browser_home)) { loadAddress(HOME_URL) },
            button(getString(R.string.browser_analyze_page)) { analyze(handle.webView.url ?: address.text.toString()) },
        ))
        useSession = CheckBox(this).apply {
            text = getString(R.string.browser_use_session)
            isChecked = false
            isEnabled = !incognito
        }
        root.addView(buttonRow(
            useSession,
            button(getString(R.string.browser_desktop)) { toggleDesktop() },
            button(getString(R.string.browser_share)) { sharePage() },
            button(getString(R.string.browser_copy)) { copyPage() },
        ))
        root.addView(buttonRow(
            TextView(this).apply { text = if (incognito) getString(R.string.browser_incognito_active) else getString(R.string.browser_session_encrypted) },
            button(getString(R.string.browser_clear_site)) { clearCurrentSite() },
            button(getString(R.string.browser_clear_all)) { vault.clearAll(); clearRuntimeSession() },
        ))
        root.addView(handle.webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(TextView(this).apply { text = getString(R.string.browser_detected_media) })
        detectedAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        root.addView(ListView(this).apply {
            adapter = detectedAdapter
            setOnItemClickListener { _, _, position, _ -> detected.values.elementAtOrNull(position)?.let { analyze(it.actualUrl) } }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.displayMetrics.density.times(150).toInt()))
        return root
    }

    private fun buttonRow(vararg views: android.view.View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(handle.webView, true)
        handle.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        handle.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                restoreFor(request.url.toString())
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                MediaRequestDetector.detect(request.url.toString(), request.requestHeaders["Accept"])?.let(::recordDetected)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                address.setText(url)
                if (!incognito) persist(url)
            }
        }
    }

    private fun loadAddress(raw: String) {
        val value = raw.trim()
        val url = when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            '.' in value && ' ' !in value -> "https://$value"
            else -> "https://www.google.com/search?q=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        restoreFor(url)
        handle.webView.loadUrl(url)
    }

    private fun recordDetected(media: DetectedMedia) = runOnUiThread {
        if (detected.putIfAbsent(media.actualUrl, media) == null) {
            detectedAdapter.add("${media.kind.uppercase()}  ${media.displayUrl}")
        }
    }

    private fun persist(url: String) {
        val host = host(url) ?: return
        cookies.getCookie(url)?.takeIf(String::isNotBlank)?.let { vault.save(host, it) }
    }

    private fun restoreFor(url: String) {
        if (incognito) return
        val host = host(url) ?: return
        vault.restore(host)?.split(';')?.forEach { cookies.setCookie(url, it.trim()) }
    }

    private fun analyze(url: String) {
        val sessionHost = if (useSession.isChecked) {
            val host = host(url)
            val header = cookies.getCookie(url)
            if (host != null && !header.isNullOrBlank()) {
                vault.save(host, header)
                host
            } else null
        } else null
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_URL, url)
            putExtra(MainActivity.EXTRA_AUTO_ANALYZE, true)
            sessionHost?.let { putExtra(MainActivity.EXTRA_SESSION_HOST, it) }
        })
        finish()
    }

    private fun toggleDesktop() {
        val settings = handle.webView.settings
        settings.userAgentString = if (settings.userAgentString.contains("Mobile")) DESKTOP_USER_AGENT else WebSettings.getDefaultUserAgent(this)
        handle.webView.reload()
    }

    private fun sharePage() = startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, handle.webView.url)
    }, null))

    private fun copyPage() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("URL", handle.webView.url))
    }

    private fun clearCurrentSite() {
        val url = handle.webView.url ?: return
        val host = host(url) ?: return
        cookies.getCookie(url).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter(String::isNotBlank).forEach {
            cookies.setCookie(url, "$it=; Max-Age=0; Path=/")
        }
        cookies.flush()
        WebStorage.getInstance().deleteOrigin("${URI(url).scheme}://$host")
        vault.clear(host)
    }

    private fun clearRuntimeSession() {
        cookies.removeAllCookies(null)
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
    }

    override fun onDestroy() {
        if (::handle.isInitialized) {
            if (!incognito) handle.webView.url?.let(::persist)
            handle.release()
        }
        clearRuntimeSession()
        super.onDestroy()
    }

    private fun host(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()

    companion object {
        const val EXTRA_URL = "browser_url"
        const val EXTRA_INCOGNITO = "browser_incognito"
        private const val HOME_URL = "https://www.google.com/"
        private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36"
    }
}
