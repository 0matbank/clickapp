package com.clickdownloader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.clickdownloader.app.download.DownloadService
import com.clickdownloader.app.ui.theme.ClickDownloaderTheme
import com.clickdownloader.core.download.CreateDirectDownloadUseCase
import com.clickdownloader.core.model.AppThemeMode
import kotlinx.coroutines.launch

class QuickAnalyzeActivity : AppCompatActivity() {
    private var candidates by mutableStateOf<List<String>>(emptyList())
    private var working by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var processedFingerprint: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processedFingerprint = savedInstanceState?.getInt(KEY_FINGERPRINT)?.takeIf { savedInstanceState.containsKey(KEY_FINGERPRINT) }
        acceptIntent(intent)
        setContent {
            ClickDownloaderTheme(themeMode = AppThemeMode.SYSTEM) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(getString(R.string.quick_analyze_title), style = MaterialTheme.typography.headlineSmall)
                    if (working) CircularProgressIndicator()
                    candidates.forEach { url ->
                        OutlinedButton(onClick = { analyze(url) }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                            Text(url, maxLines = 2)
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            startActivity(Intent(this@QuickAnalyzeActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_URL, candidates.firstOrNull()))
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(getString(R.string.open_full_app)) }
                    OutlinedButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text(getString(R.string.cancel)) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        processedFingerprint?.let { outState.putInt(KEY_FINGERPRINT, it) }
        super.onSaveInstanceState(outState)
    }

    private fun acceptIntent(intent: Intent) {
        val fingerprint = intent.filterHashCode() * 31 + (intent.getStringExtra(Intent.EXTRA_TEXT)?.hashCode() ?: 0)
        if (processedFingerprint == fingerprint) return
        processedFingerprint = fingerprint
        candidates = extractUrls(intent).distinct()
        error = if (candidates.isEmpty()) getString(R.string.invalid_url) else null
    }

    private fun analyze(url: String) {
        working = true
        error = null
        val container = (application as ClickDownloaderApplication).container
        val useCase = CreateDirectDownloadUseCase(container.downloadJobRepository, container.downloadRequestRepository, container.directMediaProbe, BuildConfig.VERSION_NAME)
        lifecycleScope.launch {
            useCase(url)
                .onSuccess {
                    DownloadService.start(this@QuickAnalyzeActivity)
                    finish()
                }
                .onFailure {
                    startActivity(
                        Intent(this@QuickAnalyzeActivity, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_URL, url)
                            .putExtra(MainActivity.EXTRA_AUTO_ANALYZE, true),
                    )
                    finish()
                }
        }
    }

    private fun extractUrls(intent: Intent): List<String> {
        val text = buildList {
            intent.dataString?.let(::add)
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::add)
            val clip = intent.clipData
            if (clip != null) for (index in 0 until clip.itemCount) add(clip.getItemAt(index).coerceToText(this@QuickAnalyzeActivity).toString())
        }.joinToString("\n")
        return URL_PATTERN.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', '}') }.toList()
    }

    companion object {
        private const val KEY_FINGERPRINT = "processed_intent_fingerprint"
        private val URL_PATTERN = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
    }
}
