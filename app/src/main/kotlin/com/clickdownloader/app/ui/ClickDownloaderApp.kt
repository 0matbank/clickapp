package com.clickdownloader.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Slider
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
import com.clickdownloader.app.BrowserActivity
import com.clickdownloader.app.PlayerActivity
import com.clickdownloader.app.download.DownloadService
import com.clickdownloader.app.bubble.BubbleOverlayService
import com.clickdownloader.app.media.CompatibleCopyService
import com.clickdownloader.core.model.AppLanguage
import com.clickdownloader.core.model.AppThemeMode
import com.clickdownloader.core.model.DownloadJob
import com.clickdownloader.core.model.DownloadJobState
import com.clickdownloader.core.model.FormatCompatibility
import com.clickdownloader.core.model.MediaFormatOption
import com.clickdownloader.core.model.LibraryMedia
import com.clickdownloader.core.extractor.BatchQualityRule
import com.clickdownloader.core.extractor.ExtractorUpdateChannel

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
    val shareMediaLabel = stringResource(R.string.share_media)
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
                UiMessage.SOURCE_UNAVAILABLE -> R.string.source_unavailable
                UiMessage.SESSION_REQUIRED -> R.string.session_required
                UiMessage.DOWNLOAD_QUEUED -> R.string.download_queued
                UiMessage.BATCH_PARTIAL -> R.string.batch_partial
                UiMessage.FOLDER_SAVED -> R.string.folder_saved
                UiMessage.FOLDER_ERROR -> R.string.folder_error
                UiMessage.BUBBLE_SHARE_FALLBACK -> R.string.bubble_share_fallback
                UiMessage.CONVERSION_PREFLIGHT_FAILED -> R.string.conversion_preflight_failed
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
                        viewModel.analyze {
                            DownloadService.start(context)
                            navController.navigate(Destination.DOWNLOADS.route)
                        }
                    },
                    onFormatSelected = { format ->
                        viewModel.selectFormat(format) {
                            DownloadService.start(context)
                            navController.navigate(Destination.DOWNLOADS.route)
                        }
                    },
                    onAudioSelected = { format ->
                        viewModel.selectCompanionAudio(format) {
                            DownloadService.start(context)
                            navController.navigate(Destination.DOWNLOADS.route)
                        }
                    },
                    onBackToFormats = viewModel::backToFormats,
                    onPlaylistItemToggled = viewModel::togglePlaylistItem,
                    onBatchRuleSelected = viewModel::setBatchRule,
                    onPrepareBatch = viewModel::prepareBatch,
                    onConfirmBatch = {
                        viewModel.confirmBatch {
                            DownloadService.start(context)
                            navController.navigate(Destination.DOWNLOADS.route)
                        }
                    },
                    onDismissBatch = viewModel::dismissBatchConfirmation,
                    onOpenBrowser = { incognito ->
                        context.startActivity(Intent(context, BrowserActivity::class.java).apply {
                            putExtra(BrowserActivity.EXTRA_INCOGNITO, incognito)
                            state.inputUrl.trim().takeIf(String::isNotBlank)?.let { putExtra(BrowserActivity.EXTRA_URL, it) }
                        })
                    },
                )
            }
            composable(Destination.DOWNLOADS.route) {
                DownloadsScreen(
                    jobs = state.jobs,
                    liveJobIds = state.liveJobIds,
                    onPause = { sendDownloadAction(context, DownloadService.ACTION_PAUSE, it) },
                    onResume = { sendDownloadAction(context, DownloadService.ACTION_RESUME, it) },
                    onRetry = { sendDownloadAction(context, DownloadService.ACTION_RETRY, it) },
                    onCancel = { sendDownloadAction(context, DownloadService.ACTION_CANCEL, it) },
                    onFinalizeLive = { sendDownloadAction(context, DownloadService.ACTION_FINALIZE_LIVE, it) },
                )
            }
            composable(Destination.LIBRARY.route) {
                LibraryScreen(
                    files = state.library,
                    onPlay = { media ->
                        context.startActivity(Intent(context, PlayerActivity::class.java)
                            .putExtra(PlayerActivity.EXTRA_URI, media.uri)
                            .putExtra(PlayerActivity.EXTRA_TITLE, media.displayName))
                    },
                    onShare = { media ->
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = media.mimeType.ifBlank { "video/*" }
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(media.uri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, shareMediaLabel))
                    },
                    onCompatibleCopy = viewModel::prepareCompatibleCopy,
                )
            }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    onLanguageSelected = viewModel::setLanguage,
                    onThemeSelected = viewModel::setTheme,
                    onAskQualityChanged = viewModel::setAskQualityEveryTime,
                    onFolderSelected = viewModel::selectDownloadDirectory,
                    onBubbleEnabledChanged = { enabled ->
                        viewModel.setBubbleEnabled(enabled)
                        if (enabled) BubbleOverlayService.show(context) else BubbleOverlayService.hide(context)
                    },
                    onBubbleOpacityChanged = viewModel::setBubbleOpacity,
                    onBubbleSizeChanged = viewModel::setBubbleSizeDp,
                    onAllowLowBatteryConversionChanged = viewModel::setAllowConversionOnLowBattery,
                    onAllowHotConversionChanged = viewModel::setAllowConversionWhenHot,
                    onPauseDownloadsOnLowBatteryChanged = viewModel::setPauseDownloadsOnLowBattery,
                    onReadExtractorVersion = viewModel::readExtractorVersion,
                    onUpdateExtractor = viewModel::updateExtractor,
                    onRollbackExtractor = viewModel::rollbackExtractor,
                )
            }
        }
    }
    state.compatibleCopyPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCompatibleCopy,
            title = { Text(stringResource(R.string.compatible_copy_title)) },
            text = {
                Text(stringResource(
                    R.string.compatible_copy_preflight,
                    humanSize(prompt.preflight.requiredFreeBytes),
                    formatDuration(prompt.preflight.estimatedMillis),
                    prompt.preflight.batteryPercent?.let { "$it%" } ?: stringResource(R.string.unknown_value),
                    if (prompt.preflight.deviceHot) stringResource(R.string.device_hot) else stringResource(R.string.device_temperature_ok),
                ))
            },
            confirmButton = {
                Button(
                    enabled = prompt.preflight.availableBytes >= prompt.preflight.requiredFreeBytes,
                    onClick = {
                        CompatibleCopyService.start(context, prompt.media.uri, prompt.media.jobId, prompt.media.displayName)
                        viewModel.dismissCompatibleCopy()
                    },
                ) { Text(stringResource(R.string.create_compatible_copy)) }
            },
            dismissButton = { OutlinedButton(onClick = viewModel::dismissCompatibleCopy) { Text(stringResource(R.string.cancel)) } },
        )
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
    onFormatSelected: (MediaFormatOption) -> Unit,
    onAudioSelected: (MediaFormatOption) -> Unit,
    onBackToFormats: () -> Unit,
    onPlaylistItemToggled: (String) -> Unit,
    onBatchRuleSelected: (BatchQualityRule) -> Unit,
    onPrepareBatch: () -> Unit,
    onConfirmBatch: () -> Unit,
    onDismissBatch: () -> Unit,
    onOpenBrowser: (Boolean) -> Unit,
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
                Button(onClick = onAnalyze, enabled = !state.isAnalyzing && state.inputUrl.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.analyze))
                }
            }
        }
        if (state.isAnalyzing) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.extractor_loading))
                }
            }
        }
        state.pendingSelection?.let { pending ->
            item {
                Text(pending.analysis.metadata.title, style = MaterialTheme.typography.titleLarge)
                if (pending.analysis.isPlaylist) {
                    Text(stringResource(R.string.playlist_items_count, pending.analysis.playlistItems.size))
                } else {
                    Text(stringResource(R.string.source_formats_count, pending.analysis.formats.size))
                }
            }
            val selectedVideo = state.selectedVideo
            if (pending.analysis.isPlaylist) {
                item {
                    Text(stringResource(R.string.batch_quality_rule), style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(BatchQualityRule.entries) { rule ->
                            FilterChip(
                                selected = state.batchRule == rule,
                                onClick = { onBatchRuleSelected(rule) },
                                label = { Text(batchRuleLabel(rule)) },
                            )
                        }
                    }
                }
                items(pending.analysis.playlistItems, key = { "playlist-${it.id}" }) { playlistItem ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = playlistItem.id in state.selectedPlaylistIds,
                                onCheckedChange = { onPlaylistItemToggled(playlistItem.id) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(playlistItem.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                playlistItem.durationMillis?.let { Text("${it / 1000}s", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.batch_rule_disclosure), style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = onPrepareBatch,
                        enabled = state.selectedPlaylistIds.isNotEmpty() && !state.isAnalyzing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.prepare_selected_items, state.selectedPlaylistIds.size)) }
                }
            } else if (selectedVideo == null) {
                val recommendedVideoId = pending.analysis.formats
                    .filter { it.hasVideo && !it.drmProtected && it.compatibility != FormatCompatibility.TRANSCODE_REQUIRED }
                    .maxByOrNull { (it.width ?: 0).toLong() * (it.height ?: 0) * 100 + (it.framesPerSecond ?: 0.0).toLong() }
                    ?.formatId
                val recommendedAudioId = pending.analysis.formats.filter { it.isAudioOnly && !it.drmProtected }
                    .maxByOrNull { it.audioBitrate ?: 0L }?.formatId
                items(pending.analysis.formats, key = { it.formatId }) { format ->
                    FormatCard(format = format, recommended = format.formatId == recommendedVideoId || format.formatId == recommendedAudioId, onClick = { onFormatSelected(format) })
                }
            } else {
                item {
                    Text(stringResource(R.string.choose_audio_track), style = MaterialTheme.typography.titleMedium)
                    Text(formatSummary(selectedVideo), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onBackToFormats) { Text(stringResource(R.string.back_to_formats)) }
                }
                items(pending.analysis.formats.filter(MediaFormatOption::isAudioOnly), key = { "audio-${it.formatId}" }) { audio ->
                    val recommended = audio == pending.analysis.formats.filter(MediaFormatOption::isAudioOnly).maxByOrNull { it.audioBitrate ?: 0L }
                    FormatCard(format = audio, recommended = recommended, onClick = { onAudioSelected(audio) })
                }
            }
        }
        item { Text(stringResource(R.string.share_instruction), style = MaterialTheme.typography.bodySmall) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onOpenBrowser(false) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.open_browser)) }
                OutlinedButton(onClick = { onOpenBrowser(true) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.open_incognito)) }
            }
        }
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
    state.batchPreparation?.let { batch ->
        AlertDialog(
            onDismissRequest = onDismissBatch,
            title = { Text(stringResource(R.string.confirm_batch_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_batch_body,
                        batch.items.size,
                        if (batch.allSizesKnown) humanSize(batch.knownBytes) else stringResource(R.string.size_partly_unknown),
                        batch.failedItemIds.size,
                    ),
                )
            },
            confirmButton = { Button(onClick = onConfirmBatch, enabled = batch.items.isNotEmpty()) { Text(stringResource(R.string.confirm_queue)) } },
            dismissButton = { OutlinedButton(onClick = onDismissBatch) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun batchRuleLabel(rule: BatchQualityRule): String = stringResource(
    when (rule) {
        BatchQualityRule.BEST_SOURCE_WITH_AUDIO -> R.string.batch_best_source
        BatchQualityRule.BEST_MUXED -> R.string.batch_best_muxed
        BatchQualityRule.AUDIO_ONLY -> R.string.batch_audio_only
    },
)

@Composable
private fun FormatCard(format: MediaFormatOption, recommended: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(formatSummary(format), style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    format.videoCodec,
                    format.audioCodec,
                    format.framesPerSecond?.let { "${it}fps" },
                    format.dynamicRange,
                    format.bitDepth?.let { "${it}-bit" },
                    format.audioLanguage,
                    format.estimatedBytes?.let(::humanSize),
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(compatibilityLabel(format.compatibility), color = MaterialTheme.colorScheme.primary)
            if (recommended) Text(stringResource(R.string.recommended_for_device), color = MaterialTheme.colorScheme.tertiary)
            Button(onClick = onClick, enabled = !format.drmProtected) {
                Text(if (format.hasVideo && !format.hasAudio) stringResource(R.string.select_video_then_audio) else stringResource(R.string.download_exact_format))
            }
        }
    }
}

private fun formatSummary(format: MediaFormatOption): String = listOfNotNull(
    format.height?.let { "${it}p" },
    format.formatNote,
    format.extension?.uppercase(),
    format.formatId,
    if (format.isAudioOnly) "audio-only" else null,
).joinToString(" • ")

private fun compatibilityLabel(value: FormatCompatibility): String = when (value) {
    FormatCompatibility.DIRECT -> "Device compatible"
    FormatCompatibility.REMUX_REQUIRED -> "Lossless remux required"
    FormatCompatibility.TRANSCODE_REQUIRED -> "Conversion required"
    FormatCompatibility.UNSUPPORTED_DRM -> "DRM protected — unavailable"
    FormatCompatibility.UNKNOWN -> "Compatibility unknown"
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
    liveJobIds: Set<String>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onFinalizeLive: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(DownloadTab.ACTIVE) }
    val filtered = jobs.filter { job ->
        when (selectedTab) {
            DownloadTab.ACTIVE -> job.state !in setOf(DownloadJobState.QUEUED, DownloadJobState.COMPLETED, DownloadJobState.FAILED, DownloadJobState.CANCELLED)
            DownloadTab.QUEUE -> job.state == DownloadJobState.QUEUED
            DownloadTab.COMPLETED -> job.state == DownloadJobState.COMPLETED || job.state == DownloadJobState.PLAYLIST_QUEUED
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
                            DownloadJobState.DOWNLOADING_VIDEO, DownloadJobState.DOWNLOADING_AUDIO, DownloadJobState.DOWNLOADING_FRAGMENTS, DownloadJobState.MERGING ->
                                if (it.id in liveJobIds) {
                                    OutlinedButton(onClick = { onFinalizeLive(it.id) }) { Text(stringResource(R.string.stop_and_save)) }
                                } else {
                                    OutlinedButton(onClick = { onPause(it.id) }) { Text(stringResource(R.string.pause)) }
                                }
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
private fun LibraryScreen(
    files: List<LibraryMedia>,
    onPlay: (LibraryMedia) -> Unit,
    onShare: (LibraryMedia) -> Unit,
    onCompatibleCopy: (LibraryMedia) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TopAppBar(title = { Text(stringResource(R.string.library_title)) }) }
        if (files.isEmpty()) item {
            Text(text = stringResource(R.string.library_empty), modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
        }
        items(files, key = { it.id }) { media ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(media.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${humanSize(media.sizeBytes)} • ${if (media.verified) stringResource(R.string.verified) else stringResource(R.string.not_verified)}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPlay(media) }) { Text(stringResource(R.string.play)) }
                        OutlinedButton(onClick = { onShare(media) }) { Text(stringResource(R.string.share_media)) }
                        if (media.mimeType.startsWith("video/")) {
                            OutlinedButton(onClick = { onCompatibleCopy(media) }) { Text(stringResource(R.string.make_compatible_copy)) }
                        }
                    }
                }
            }
        }
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
    onBubbleEnabledChanged: (Boolean) -> Unit,
    onBubbleOpacityChanged: (Float) -> Unit,
    onBubbleSizeChanged: (Int) -> Unit,
    onAllowLowBatteryConversionChanged: (Boolean) -> Unit,
    onAllowHotConversionChanged: (Boolean) -> Unit,
    onPauseDownloadsOnLowBatteryChanged: (Boolean) -> Unit,
    onReadExtractorVersion: () -> Unit,
    onUpdateExtractor: (ExtractorUpdateChannel) -> Unit,
    onRollbackExtractor: () -> Unit,
) {
    val context = LocalContext.current
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolderSelected(uri.toString())
    }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) onBubbleEnabledChanged(true)
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
                Text(stringResource(R.string.storage_permission_help), style = MaterialTheme.typography.bodySmall)
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
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.settings_bubble), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.bubble_privacy_explanation), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.enable_bubble), modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.settings.bubbleEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) onBubbleEnabledChanged(false)
                            else if (Settings.canDrawOverlays(context)) onBubbleEnabledChanged(true)
                            else overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        },
                    )
                }
                Text(stringResource(R.string.bubble_opacity, (state.settings.bubbleOpacity * 100).toInt()))
                Slider(value = state.settings.bubbleOpacity, onValueChange = onBubbleOpacityChanged, valueRange = .35f..1f)
                Text(stringResource(R.string.bubble_size, state.settings.bubbleSizeDp))
                Slider(value = state.settings.bubbleSizeDp.toFloat(), onValueChange = { onBubbleSizeChanged(it.toInt()) }, valueRange = 40f..80f, steps = 7)
            }
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.settings_conversion), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.allow_low_battery_conversion), modifier = Modifier.weight(1f))
                    Switch(checked = state.settings.allowConversionOnLowBattery, onCheckedChange = onAllowLowBatteryConversionChanged)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.allow_hot_conversion), modifier = Modifier.weight(1f))
                    Switch(checked = state.settings.allowConversionWhenHot, onCheckedChange = onAllowHotConversionChanged)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pause_downloads_low_battery), modifier = Modifier.weight(1f))
                    Switch(checked = state.settings.pauseDownloadsOnLowBattery, onCheckedChange = onPauseDownloadsOnLowBatteryChanged)
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_extractor), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.extractor_update_help), style = MaterialTheme.typography.bodySmall)
                state.extractorStatus.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !state.extractorStatus.running, onClick = onReadExtractorVersion) { Text(stringResource(R.string.extractor_version)) }
                    Button(enabled = !state.extractorStatus.running, onClick = { onUpdateExtractor(ExtractorUpdateChannel.STABLE) }) { Text(stringResource(R.string.extractor_update_stable)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !state.extractorStatus.running, onClick = { onUpdateExtractor(ExtractorUpdateChannel.BETA) }) { Text(stringResource(R.string.extractor_update_beta)) }
                    OutlinedButton(enabled = !state.extractorStatus.running, onClick = onRollbackExtractor) { Text(stringResource(R.string.extractor_rollback)) }
                }
                if (state.extractorStatus.running) CircularProgressIndicator()
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(1)
    return "$minutes min"
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
        DownloadJobState.PLAYLIST_QUEUED -> R.string.job_playlist_queued
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
