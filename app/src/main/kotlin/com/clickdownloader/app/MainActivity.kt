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

class MainActivity : AppCompatActivity() {
    private var incomingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUrl = intent.getStringExtra(EXTRA_URL)
        val container = (application as ClickDownloaderApplication).container
        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(incomingUrl) { incomingUrl?.let(viewModel::setInputUrl) }
            ClickDownloaderTheme(themeMode = state.settings.themeMode) {
                ClickDownloaderApp(state = state, viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUrl = intent.getStringExtra(EXTRA_URL)
    }

    companion object { const val EXTRA_URL = "incoming_url" }
}
