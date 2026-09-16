package com.clickdownloader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clickdownloader.app.ui.ClickDownloaderApp
import com.clickdownloader.app.ui.theme.ClickDownloaderTheme
import com.clickdownloader.app.download.DownloadService

class MainActivity : AppCompatActivity() {
    private var incomingUrl by mutableStateOf<String?>(null)
    private var autoAnalyze by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUrl = intent.getStringExtra(EXTRA_URL)
        autoAnalyze = intent.getBooleanExtra(EXTRA_AUTO_ANALYZE, false)
        val container = (application as ClickDownloaderApplication).container
        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(incomingUrl, autoAnalyze) {
                incomingUrl?.let {
                    viewModel.setInputUrl(it)
                    if (autoAnalyze) viewModel.analyze { DownloadService.start(this@MainActivity) }
                }
            }
            ClickDownloaderTheme(themeMode = state.settings.themeMode) {
                ClickDownloaderApp(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl = intent.getStringExtra(EXTRA_URL)
        autoAnalyze = intent.getBooleanExtra(EXTRA_AUTO_ANALYZE, false)
    }

    companion object {
        const val EXTRA_URL = "incoming_url"
        const val EXTRA_AUTO_ANALYZE = "auto_analyze"
    }
}
