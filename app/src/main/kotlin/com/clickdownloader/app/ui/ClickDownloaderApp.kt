package com.clickdownloader.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.os.Build
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clickdownloader.app.MainUiState
import com.clickdownloader.app.MainViewModel
import com.clickdownloader.app.R
import com.clickdownloader.app.UiMessage
import com.clickdownloader.app.download.DownloadService
import com.clickdownloader.core.model.AppLanguage
import com.clickdownloader.core.model.AppThemeMode
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState

private enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Default.Home),
    DOWNLOADS("downloads", R.string.nav_downloads, Icons.AutoMirrored.Filled.List),
    LIBRARY("library", R.string.nav_library, Icons.Default.PlayArrow),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings),
}

@Composable
fun ClickDownloaderApp(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(state.settings.language) {
        val tags = when (state.settings.language) {
            AppLanguage.SYSTEM -> ""
            AppLanguage.ENGLISH -> "en"
            AppLanguage.BANGLA -> "bn"
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
    }

    val messageText = state.message?.let { message ->
        stringResource(
            when (message) {
                UiMessage.INVALID_URL -> R.string.invalid_url
                UiMessage.ANALYZE_FAILED -> R.string.direct_analyze_failed
                UiMessage.DOWNLOAD_QUEUED -> R.string.download_queued
                UiMessage.FOLDER_SAVED -> R.string.folder_saved
                UiMessage.FOLDER_ERROR -> R.string.folder_error
            },
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(Destination.HOME.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(
                    state = state,
                    onUrlChanged = viewModel::setInputUrl,
                    onPaste = { viewModel.setInputUrl(readClipboardText(context)) },
                    onAnalyze = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        viewModel.analyzeDirect {
                            DownloadService.start(context)
                            navController.navigate(Destination.DOWNLOADS.route)
                        }
                    },
                )
            }
            composable(Destination.DOWNLOADS.route) {
                DownloadsScreen(
                    jobs = state.jobs,
                    onPause = { sendDownloadAction(context, DownloadService.ACTION_PAUSE, it) },
                    onResume = { sendDownloadAction(context, DownloadService.ACTION_RESUME, it) },
                    onRetry = { sendDownloadAction(context, DownloadService.ACTION_RETRY, it) },
                    onCancel = { sendDownloadAction(context, DownloadService.ACTION_CANCEL, it) },
                )
            }
            composable(Destination.LIBRARY.route) { LibraryScreen() }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    onLanguageSelected = viewModel::setLanguage,
                    onThemeSelected = viewModel::setTheme,
                    onAskQualityChanged = viewModel::setAskQualityEveryTime,
                    onFolderSelected = viewModel::selectDownloadDirectory,
                )
            }
        }
    }
}

private fun readClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: MainUiState,
    onUrlChanged: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
) {
    val active = state.jobs.firstOrNull { !it.state.isTerminal && it.state != DownloadJobState.FAILED }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_supporting), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = state.inputUrl,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.url_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.paste))
                }
                Button(onClick = onAnalyze, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.analyze))
                }
            }
        }
        item { Text(stringResource(R.string.share_instruction), style = MaterialTheme.typography.bodySmall) }
        active?.let { job ->
            item {
                Text(stringResource(R.string.active_jobs), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                JobCard(job)
            }
        }
        if (state.jobs.isNotEmpty()) {
            item { Text(stringResource(R.string.recent_jobs), style = MaterialTheme.typography.titleMedium) }
            items(state.jobs.take(5), key = { it.id }) { JobCard(it) }
        } else {
            item { EmptyJobs() }
        }
    }
}

private enum class DownloadTab(val labelRes: Int) {
    ACTIVE(R.string.tab_active),
    QUEUE(R.string.tab_queue),
    COMPLETED(R.string.tab_completed),
    FAILED(R.string.tab_failed),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreen(
    jobs: List<DownloadJob>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(DownloadTab.ACTIVE) }
    val filtered = jobs.filter { job ->
        when (selectedTab) {
            DownloadTab.ACTIVE -> job.state !in setOf(DownloadJobState.QUEUED, DownloadJobState.COMPLETED, DownloadJobState.FAILED, DownloadJobState.CANCELLED)
            DownloadTab.QUEUE -> job.state == DownloadJobState.QUEUED
            DownloadTab.COMPLETED -> job.state == DownloadJobState.COMPLETED
            DownloadTab.FAILED -> job.state == DownloadJobState.FAILED || job.state == DownloadJobState.CANCELLED
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.downloads_title)) })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DownloadTab.entries) { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    label = { Text(stringResource(tab.labelRes)) },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyJobs(Modifier.padding(20.dp))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) {
                    JobCard(it)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (it.state) {
                            DownloadJobState.DOWNLOADING_VIDEO, DownloadJobState.DOWNLOADING_AUDIO ->
                                OutlinedButton(onClick = { onPause(it.id) }) { Text(stringResource(R.string.pause)) }
                            DownloadJobState.PAUSED, DownloadJobState.WAITING_FOR_NETWORK ->
                                OutlinedButton(onClick = { onResume(it.id) }) { Text(stringResource(R.string.resume)) }
                            DownloadJobState.FAILED, DownloadJobState.RETRY_SCHEDULED, DownloadJobState.LINK_EXPIRED ->
                                OutlinedButton(onClick = { onRetry(it.id) }) { Text(stringResource(R.string.retry)) }
                            else -> Unit
                        }
                        if (!it.state.isTerminal) OutlinedButton(onClick = { onCancel(it.id) }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen() {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.library_title)) })
        Text(
            text = stringResource(R.string.library_empty),
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: MainUiState,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onAskQualityChanged: (Boolean) -> Unit,
    onFolderSelected: (String) -> Unit,
) {
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolderSelected(uri.toString())
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
        item {
            SettingsChoiceSection(
                title = stringResource(R.string.settings_language),
                choices = AppLanguage.entries,
                selected = state.settings.language,
                label = { languageLabel(it) },
                onSelected = onLanguageSelected,
            )
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item {
            SettingsChoiceSection(
                title = stringResource(R.string.settings_theme),
                choices = AppThemeMode.entries,
                selected = state.settings.themeMode,
                label = { themeLabel(it) },
                onSelected = onThemeSelected,
            )
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.settings_quality), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.ask_quality_every_time), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.settings.askQualityEveryTime,
                        onCheckedChange = onAskQualityChanged,
                    )
                }
                Text(stringResource(R.string.show_all_formats), style = MaterialTheme.typography.bodySmall)
            }
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = state.settings.downloadDirectoryUri ?: stringResource(R.string.default_download_folder),
                    modifier = Modifier.padding(vertical = 12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                    Text(stringResource(R.string.choose_folder))
                }
            }
        }
    }
}

@Composable
private fun <T> SettingsChoiceSection(
    title: String,
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LazyRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(choices) { _, choice ->
                FilterChip(
                    selected = selected == choice,
                    onClick = { onSelected(choice) },
                    label = { Text(label(choice)) },
                )
            }
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.BANGLA -> R.string.language_bangla
    },
)

@Composable
private fun themeLabel(theme: AppThemeMode): String = stringResource(
    when (theme) {
        AppThemeMode.SYSTEM -> R.string.theme_system
        AppThemeMode.LIGHT -> R.string.theme_light
        AppThemeMode.DARK -> R.string.theme_dark
        AppThemeMode.AMOLED -> R.string.theme_amoled
    },
)

@Composable
private fun JobCard(job: DownloadJob) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(job.sourceUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(jobStateLabel(job.state), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            val total = job.totalBytes
            if (total != null && total > 0) {
                Text(stringResource(R.string.downloaded_of_total, humanSize(job.downloadedBytes), humanSize(total)))
            } else if (job.downloadedBytes > 0) {
                Text(humanSize(job.downloadedBytes))
            }
            job.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun sendDownloadAction(context: Context, action: String, jobId: String) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, DownloadService::class.java).setAction(action).putExtra(DownloadService.EXTRA_JOB_ID, jobId),
    )
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

@Composable
private fun jobStateLabel(state: DownloadJobState): String = stringResource(
    when (state) {
        DownloadJobState.CREATED -> R.string.job_ready
        DownloadJobState.ANALYZING -> R.string.job_analyzing
        DownloadJobState.WAITING_FOR_SELECTION -> R.string.job_waiting_selection
        DownloadJobState.QUEUED -> R.string.job_queued
        DownloadJobState.PREPARING -> R.string.job_preparing
        DownloadJobState.DOWNLOADING_VIDEO -> R.string.job_downloading_video
        DownloadJobState.DOWNLOADING_AUDIO -> R.string.job_downloading_audio
        DownloadJobState.DOWNLOADING_FRAGMENTS -> R.string.job_downloading_fragments
        DownloadJobState.MERGING -> R.string.job_merging
        DownloadJobState.OPTIONAL_CONVERSION -> R.string.job_converting
        DownloadJobState.VERIFYING -> R.string.job_verifying
        DownloadJobState.COMPLETED -> R.string.job_completed
        DownloadJobState.PAUSED -> R.string.job_paused
        DownloadJobState.WAITING_FOR_NETWORK -> R.string.job_waiting_network
        DownloadJobState.AUTH_REQUIRED -> R.string.job_auth_required
        DownloadJobState.LINK_EXPIRED -> R.string.job_link_expired
        DownloadJobState.STORAGE_REQUIRED -> R.string.job_storage_required
        DownloadJobState.RETRY_SCHEDULED -> R.string.job_retry_scheduled
        DownloadJobState.FAILED -> R.string.job_failed
        DownloadJobState.CANCELLED -> R.string.job_cancelled
    },
)

@Composable
private fun EmptyJobs(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 20.dp)) {
        Text(stringResource(R.string.no_downloads_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.no_downloads_body), style = MaterialTheme.typography.bodyMedium)
    }
}
