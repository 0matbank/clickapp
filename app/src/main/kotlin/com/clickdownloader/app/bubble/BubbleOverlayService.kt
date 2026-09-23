package com.clickdownloader.app.bubble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.clickdownloader.app.ClickDownloaderApplication
import com.clickdownloader.app.MainActivity
import com.clickdownloader.app.R
import com.clickdownloader.core.model.AppSettings
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BubbleOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var bubble: BubbleView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var settingsJob: Job? = null
    private var currentSettings = AppSettings()
    private var foregroundPackage: String? = null
    private val receiverRegistered = AtomicBoolean(false)
    private val foregroundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            foregroundPackage = intent?.getStringExtra(EXTRA_FOREGROUND_PACKAGE)
            updateVisibility()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        createChannel()
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.bubble_notification_title))
            .setContentText(getString(R.string.bubble_notification_body))
            .setOngoing(true)
            .setSilent(true)
            .build())
        windowManager = getSystemService(WindowManager::class.java)
        registerForegroundReceiver()
        settingsJob = scope.launch {
            val repository = (application as ClickDownloaderApplication).container.settingsRepository
            repository.settings.collectLatest { value ->
                currentSettings = value
                if (!value.bubbleEnabled) {
                    stopSelf()
                } else {
                    ensureBubble(value)
                    updateVisibility()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            scope.launch { (application as ClickDownloaderApplication).container.settingsRepository.setBubbleEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        settingsJob?.cancel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        if (receiverRegistered.compareAndSet(true, false)) unregisterReceiver(foregroundReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureBubble(settings: AppSettings) {
        val sizePx = (settings.bubbleSizeDp * resources.displayMetrics.density).toInt()
        val existing = bubble
        if (existing == null) {
            val view = BubbleView(this).apply {
                alpha = settings.bubbleOpacity
                contentDescription = getString(R.string.bubble_content_description)
            }
            val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = saved.getInt(KEY_X, 0)
                y = saved.getInt(KEY_Y, resources.displayMetrics.heightPixels / 3)
            }
            attachGestures(view, params)
            windowManager.addView(view, params)
            bubble = view
            layoutParams = params
        } else {
            existing.alpha = settings.bubbleOpacity
            layoutParams?.let { params ->
                if (params.width != sizePx || params.height != sizePx) {
                    params.width = sizePx
                    params.height = sizePx
                    windowManager.updateViewLayout(existing, params)
                }
            }
        }
    }

    private fun attachGestures(view: BubbleView, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var downAt = 0L
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    downAt = event.eventTime
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - downX) + abs(event.rawY - downY)
                    val duration = event.eventTime - downAt
                    when {
                        moved < 20 * resources.displayMetrics.density && duration >= 650 -> stopBubble()
                        moved < 20 * resources.displayMetrics.density -> analyzeClipboard()
                        else -> snapAndPersist(params, view)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapAndPersist(params: WindowManager.LayoutParams, view: View) {
        val screenWidth = resources.displayMetrics.widthPixels
        params.x = if (params.x + view.width / 2 < screenWidth / 2) 0 else screenWidth - view.width
        params.y = params.y.coerceIn(0, resources.displayMetrics.heightPixels - view.height)
        windowManager.updateViewLayout(view, params)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
    }

    private fun analyzeClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)
        val url = BubblePolicy.firstSupportedUrl(text)
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (url != null) intent.putExtra(MainActivity.EXTRA_URL, url).putExtra(MainActivity.EXTRA_AUTO_ANALYZE, true)
        else intent.putExtra(MainActivity.EXTRA_BUBBLE_FALLBACK, true)
        startActivity(intent)
    }

    private fun stopBubble() {
        scope.launch { (application as ClickDownloaderApplication).container.settingsRepository.setBubbleEnabled(false) }
        stopSelf()
    }

    private fun updateVisibility() {
        bubble?.visibility = if (BubblePolicy.shouldShowForPackage(
                false,
                currentSettings.bubbleAllowlistedPackages,
                foregroundPackage,
                packageName,
            )) View.VISIBLE else View.GONE
    }

    private fun registerForegroundReceiver() {
        if (!receiverRegistered.compareAndSet(false, true)) return
        val filter = IntentFilter(ACTION_FOREGROUND_PACKAGE)
        ContextCompat.registerReceiver(this, foregroundReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.bubble_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val ACTION_SHOW = "com.clickdownloader.app.action.SHOW_BUBBLE"
        const val ACTION_HIDE = "com.clickdownloader.app.action.HIDE_BUBBLE"
        const val ACTION_FOREGROUND_PACKAGE = "com.clickdownloader.app.action.FOREGROUND_PACKAGE"
        const val EXTRA_FOREGROUND_PACKAGE = "foreground_package"
        private const val CHANNEL_ID = "floating_bubble"
        private const val NOTIFICATION_ID = 7201
        private const val PREFS = "bubble_position"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        fun show(context: Context) {
            val intent = Intent(context, BubbleOverlayService::class.java).setAction(ACTION_SHOW)
            ContextCompat.startForegroundService(context, intent)
        }

        fun hide(context: Context) {
            context.startService(Intent(context, BubbleOverlayService::class.java).setAction(ACTION_HIDE))
        }
    }
}
