package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AppNotification
import com.example.service.ScreenOffMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AlertPrefs(
    val taskTuneUri: String? = null,
    val taskTuneTitle: String = "Default Notification Sound",
    val messageTuneUri: String? = null,
    val messageTuneTitle: String = "Default Notification Sound",
    val autoSmsPromptForTasksAndMessages: Boolean = false,
    val wakeScreenOnAlert: Boolean = true,
    val screenOffSyncServiceEnabled: Boolean = false
)

object NotificationAlertHelper {
    const val SILENT_SENTINEL = "__silent__"

    private const val PREFS_NAME = "super_service_alert_prefs"
    private const val KEY_TASK_TUNE_URI = "task_tune_uri"
    private const val KEY_TASK_TUNE_TITLE = "task_tune_title"
    private const val KEY_MSG_TUNE_URI = "msg_tune_uri"
    private const val KEY_MSG_TUNE_TITLE = "msg_tune_title"
    private const val KEY_AUTO_SMS = "auto_sms_prompt"
    private const val KEY_WAKE_SCREEN = "wake_screen_on_alert"
    private const val KEY_SCREEN_OFF_SERVICE = "screen_off_sync_service"
    private const val KEY_TASK_CHANNEL_VER = "task_channel_ver"
    private const val KEY_MSG_CHANNEL_VER = "msg_channel_ver"

    const val MONITOR_CHANNEL_ID = "super_service_monitor_channel"

    private val _prefsFlow = MutableStateFlow(AlertPrefs())
    val prefsFlow: StateFlow<AlertPrefs> = _prefsFlow.asStateFlow()

    private var activeRingtone: Ringtone? = null
    private var activePlayer: MediaPlayer? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _prefsFlow.value = AlertPrefs(
            taskTuneUri = prefs.getString(KEY_TASK_TUNE_URI, null),
            taskTuneTitle = prefs.getString(KEY_TASK_TUNE_TITLE, "Default Notification Sound")
                ?: "Default Notification Sound",
            messageTuneUri = prefs.getString(KEY_MSG_TUNE_URI, null),
            messageTuneTitle = prefs.getString(KEY_MSG_TUNE_TITLE, "Default Notification Sound")
                ?: "Default Notification Sound",
            autoSmsPromptForTasksAndMessages = prefs.getBoolean(KEY_AUTO_SMS, false),
            wakeScreenOnAlert = prefs.getBoolean(KEY_WAKE_SCREEN, true),
            screenOffSyncServiceEnabled = prefs.getBoolean(KEY_SCREEN_OFF_SERVICE, false)
        )
        ensureChannels(appContext)
    }

    private fun getTaskChannelId(context: Context): String {
        val ver = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_TASK_CHANNEL_VER, 1)
        return "super_service_tasks_v$ver"
    }

    private fun getMessageChannelId(context: Context): String {
        val ver = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MSG_CHANNEL_VER, 1)
        return "super_service_messages_v$ver"
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        val prefs = _prefsFlow.value

        createAlertChannel(
            nm = nm,
            channelId = getTaskChannelId(appContext),
            name = "Super Service Task & Shift Alerts",
            description = "Task assignments, calendar reminders, and shift auto check-out alerts.",
            tuneUri = prefs.taskTuneUri
        )

        createAlertChannel(
            nm = nm,
            channelId = getMessageChannelId(appContext),
            name = "Super Service Messages & Broadcasts",
            description = "Internal messages, call logs, and official Admin broadcasts.",
            tuneUri = prefs.messageTuneUri
        )

        val monitorChannel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            "Real-Time Screen-Off Alert Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps real-time Super Service notification sync active."
            setShowBadge(false)
        }
        nm.createNotificationChannel(monitorChannel)
    }

    private fun createAlertChannel(
        nm: NotificationManager,
        channelId: String,
        name: String,
        description: String,
        tuneUri: String?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = description
            enableVibration(true)
            enableLights(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            when (tuneUri) {
                SILENT_SENTINEL -> setSound(null, null)
                null -> setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttrs)
                else -> setSound(Uri.parse(tuneUri), audioAttrs)
            }
        }
        nm.createNotificationChannel(channel)
    }

    private fun bumpChannelVersion(context: Context, isTask: Boolean) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (isTask) KEY_TASK_CHANNEL_VER else KEY_MSG_CHANNEL_VER
        val oldVer = prefs.getInt(key, 1)
        val oldId = if (isTask) "super_service_tasks_v$oldVer" else "super_service_messages_v$oldVer"
        prefs.edit().putInt(key, oldVer + 1).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(oldId)
        }
        ensureChannels(appContext)
    }

    fun setTaskSilent(context: Context) {
        setTaskTuneInternal(context, SILENT_SENTINEL, "Silent")
    }

    fun setTaskTune(context: Context, uri: Uri?, customTitle: String? = null) {
        val title = customTitle ?: resolveTitle(context, uri)
        setTaskTuneInternal(context, uri?.toString(), title)
    }

    private fun setTaskTuneInternal(context: Context, uriStr: String?, title: String) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TASK_TUNE_URI, uriStr)
            .putString(KEY_TASK_TUNE_TITLE, title)
            .apply()
        _prefsFlow.value = _prefsFlow.value.copy(taskTuneUri = uriStr, taskTuneTitle = title)
        bumpChannelVersion(appContext, isTask = true)
    }

    fun setMessageSilent(context: Context) {
        setMessageTuneInternal(context, SILENT_SENTINEL, "Silent")
    }

    fun setMessageTune(context: Context, uri: Uri?, customTitle: String? = null) {
        val title = customTitle ?: resolveTitle(context, uri)
        setMessageTuneInternal(context, uri?.toString(), title)
    }

    private fun setMessageTuneInternal(context: Context, uriStr: String?, title: String) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MSG_TUNE_URI, uriStr)
            .putString(KEY_MSG_TUNE_TITLE, title)
            .apply()
        _prefsFlow.value = _prefsFlow.value.copy(messageTuneUri = uriStr, messageTuneTitle = title)
        bumpChannelVersion(appContext, isTask = false)
    }

    fun setAutoSmsPrompt(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SMS, enabled).apply()
        _prefsFlow.value = _prefsFlow.value.copy(autoSmsPromptForTasksAndMessages = enabled)
    }

    fun setWakeScreenOnAlert(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WAKE_SCREEN, enabled).apply()
        _prefsFlow.value = _prefsFlow.value.copy(wakeScreenOnAlert = enabled)
    }

    fun setScreenOffSyncServiceEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SCREEN_OFF_SERVICE, enabled).apply()
        _prefsFlow.value = _prefsFlow.value.copy(screenOffSyncServiceEnabled = enabled)
        syncScreenOffMonitorService(appContext)
    }

    fun syncScreenOffMonitorService(context: Context) {
        val appContext = context.applicationContext
        val enabled = _prefsFlow.value.screenOffSyncServiceEnabled
        val hasPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val serviceIntent = Intent(appContext, ScreenOffMonitorService::class.java)
        if (enabled && hasPerm) {
            runCatching {
                ContextCompat.startForegroundService(appContext, serviceIntent)
            }
        } else {
            runCatching {
                appContext.stopService(serviceIntent)
            }
        }
    }

    fun previewTune(context: Context, tuneUri: String?) {
        stopPreview()
        if (tuneUri == SILENT_SENTINEL) return
        val appContext = context.applicationContext
        val targetUri = tuneUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        runCatching {
            val ringtone = RingtoneManager.getRingtone(appContext, targetUri)
            if (ringtone != null) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                ringtone.play()
                activeRingtone = ringtone
            } else {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(appContext, targetUri)
                    setOnCompletionListener { it.release(); if (activePlayer === it) activePlayer = null }
                    prepare()
                    start()
                }
                activePlayer = player
            }
        }.onFailure {
            runCatching {
                val player = MediaPlayer().apply {
                    setDataSource(appContext, targetUri)
                    setOnCompletionListener { it.release(); if (activePlayer === it) activePlayer = null }
                    prepare()
                    start()
                }
                activePlayer = player
            }
        }
    }

    fun stopPreview() {
        runCatching { activeRingtone?.stop() }
        activeRingtone = null
        runCatching {
            activePlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        activePlayer = null
    }

    fun showTaskNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int
    ) {
        showSystemNotification(
            context = context,
            channelId = getTaskChannelId(context),
            title = title,
            body = body,
            notificationId = notificationId
        )
    }

    fun showMessageNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int
    ) {
        showSystemNotification(
            context = context,
            channelId = getMessageChannelId(context),
            title = title,
            body = body,
            notificationId = notificationId
        )
    }

    private fun showSystemNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        notificationId: Int
    ) {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        wakeScreenBrieflyIfEnabled(appContext)

        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBrieflyIfEnabled(context: Context) {
        if (!_prefsFlow.value.wakeScreenOnAlert) return
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (!pm.isInteractive) {
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "SuperService:AlertWakeLock"
                )
                wakeLock.acquire(3000L)
            }
        }
    }

    fun cancelNotificationForRecord(context: Context, alert: AppNotification) {
        runCatching {
            NotificationManagerCompat.from(context.applicationContext)
                .cancel(alert.id.hashCode())
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun openNotificationChannelSettings(context: Context, isTask: Boolean) {
        ensureChannels(context)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(
                    Settings.EXTRA_CHANNEL_ID,
                    if (isTask) getTaskChannelId(context) else getMessageChannelId(context)
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(intent) }
    }

    fun launchSmsComposer(context: Context, phoneNumber: String?, body: String): Boolean {
        val target = phoneNumber?.trim().orEmpty()
        val uri = Uri.parse("smsto:${Uri.encode(target)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun resolveTitle(context: Context, uri: Uri?): String {
        if (uri == null) return "Default Notification Sound"
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Custom Audio"
    }
}
