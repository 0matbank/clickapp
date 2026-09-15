package com.clickdownloader.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clickdownloader.app.ui.ClickDownloaderApp
import com.clickdownloader.app.ui.theme.ClickDownloaderTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ClickDownloaderApplication).container
        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ClickDownloaderTheme(themeMode = state.settings.themeMode) {
                ClickDownloaderApp(state = state, viewModel = viewModel)
            }
        }
    }
}

