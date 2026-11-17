package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.example.ui.icons.AudioFile
import com.example.ui.icons.BatterySaver
import com.example.ui.icons.Campaign
import androidx.compose.material.icons.filled.Close
import com.example.ui.icons.DeleteOutline
import com.example.ui.icons.LockOpen
import com.example.ui.icons.MusicNote
import com.example.ui.icons.NotificationsActive
import com.example.ui.icons.NotificationsOff
import com.example.ui.icons.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import com.example.ui.icons.RestartAlt
import androidx.compose.material.icons.filled.Settings
import com.example.ui.icons.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppNotification
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.UiStateContent
import com.example.util.NotificationAlertHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationAlertsAndTunesDialog(
    notificationsState: UiState<List<AppNotification>>,
    isAdmin: Boolean = false,
    isGuestPortal: Boolean = false,
    onOpenBroadcastComposer: () -> Unit = {},
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllRead: () -> Unit = {},
    onRemoveNotification: (AppNotification) -> Unit = {},
    onClearReadNotifications: () -> Unit = {},
    onNotificationClick: (AppNotification) -> Unit = {},
    onFeedbackMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val alertPrefs by NotificationAlertHelper.prefsFlow.collectAsStateWithLifecycle()

    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            NotificationAlertHelper.syncScreenOffMonitorService(context)
        }
        onFeedbackMessage(
            if (granted) "Mobile & screen-off alert notifications enabled."
            else "Notification permission denied. Enable in system settings to receive pop-up alerts."
        )
    }

    val taskRingtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri == null) {
                NotificationAlertHelper.setTaskSilent(context)
                onFeedbackMessage("Task alert tune set to Silent")
            } else {
                NotificationAlertHelper.setTaskTune(context, pickedUri)
                onFeedbackMessage("Task alert tune updated")
            }
        }
    }

    val messageRingtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri == null) {
                NotificationAlertHelper.setMessageSilent(context)
                onFeedbackMessage("Message alert tune set to Silent")
            } else {
                NotificationAlertHelper.setMessageTune(context, pickedUri)
                onFeedbackMessage("Message alert tune updated")
            }
        }
    }

    val taskAudioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            NotificationAlertHelper.setTaskTune(context, uri)
            onFeedbackMessage("Selected custom mobile audio for Task Alerts")
        }
    }

    val messageAudioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            NotificationAlertHelper.setMessageTune(context, uri)
            onFeedbackMessage("Selected custom mobile audio for Message Alerts")
        }
    }

    DisposableEffect(Unit) {
        onDispose { NotificationAlertHelper.stopPreview() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                if (isGuestPortal) "My Notifications & Alerts" else "Alert Tunes & Notifications",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_alert_tunes_dialog")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    )
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Enable Mobile Alert Pop-Ups",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Allow notification permission so Super Service can deliver personal alerts and official broadcasts with sound.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Button(
                                        onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("grant_notifications_button")
                                    ) {
                                        Text("Allow Notifications")
                                    }
                                }
                            }
                        }
                    }

                    if (isGuestPortal) {
                        item {
                            NotificationInboxSection(
                                notificationsState = notificationsState,
                                isGuestPortal = true,
                                onMarkNotificationRead = onMarkNotificationRead,
                                onMarkAllRead = onMarkAllRead,
                                onRemoveNotification = onRemoveNotification,
                                onClearReadNotifications = onClearReadNotifications,
                                onNotificationClick = onNotificationClick
                            )
                        }
                    }

                    if (!isGuestPortal) {
                        item {
                            TuneSelectorCard(
                                title = "Task Notification Tune",
                                subtitle = "Plays for task assignments, calendar reminders & shift auto check-out.",
                                currentTuneTitle = alertPrefs.taskTuneTitle,
                                currentTuneUri = alertPrefs.taskTuneUri,
                                testTagPrefix = "task_tune",
                                onPickSystemRingtone = {
                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose Task Alert Tune")
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        val existing = alertPrefs.taskTuneUri
                                            ?.takeIf { it != NotificationAlertHelper.SILENT_SENTINEL }
                                            ?.let(Uri::parse)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                                    }
                                    taskRingtonePicker.launch(intent)
                                },
                                onPickMobileAudio = { taskAudioFilePicker.launch(arrayOf("audio/*")) },
                                onPreview = {
                                    NotificationAlertHelper.previewTune(context, alertPrefs.taskTuneUri)
                                    NotificationAlertHelper.showTaskNotification(
                                        context = context,
                                        title = "Super Service Task Alert Test",
                                        body = "Tune: ${alertPrefs.taskTuneTitle}",
                                        notificationId = 9101
                                    )
                                },
                                onResetDefault = {
                                    NotificationAlertHelper.setTaskTune(context, null, "Default Notification Sound")
                                    onFeedbackMessage("Task alert reset to default sound")
                                },
                                onSetSilent = {
                                    NotificationAlertHelper.setTaskSilent(context)
                                    onFeedbackMessage("Task alert muted")
                                }
                            )
                        }
                    }

                    item {
                        TuneSelectorCard(
                            title = if (isGuestPortal) "Notification Sound Tune" else "Message Notification Tune",
                            subtitle = if (isGuestPortal) {
                                "Plays for notifications made for your account and Admin broadcasts."
                            } else {
                                "Plays for internal Staff/Admin/Client messages & call logs."
                            },
                            currentTuneTitle = alertPrefs.messageTuneTitle,
                            currentTuneUri = alertPrefs.messageTuneUri,
                            testTagPrefix = "message_tune",
                            onPickSystemRingtone = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose Message Alert Tune")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    val existing = alertPrefs.messageTuneUri
                                        ?.takeIf { it != NotificationAlertHelper.SILENT_SENTINEL }
                                        ?.let(Uri::parse)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                                }
                                messageRingtonePicker.launch(intent)
                            },
                            onPickMobileAudio = { messageAudioFilePicker.launch(arrayOf("audio/*")) },
                            onPreview = {
                                NotificationAlertHelper.previewTune(context, alertPrefs.messageTuneUri)
                                NotificationAlertHelper.showMessageNotification(
                                    context = context,
                                    title = "Super Service Message Alert Test",
                                    body = "Tune: ${alertPrefs.messageTuneTitle}",
                                    notificationId = 9102
                                )
                            },
                            onResetDefault = {
                                NotificationAlertHelper.setMessageTune(context, null, "Default Notification Sound")
                                onFeedbackMessage("Message alert reset to default sound")
                            },
                            onSetSilent = {
                                NotificationAlertHelper.setMessageSilent(context)
                                onFeedbackMessage("Message alert muted")
                            }
                        )
                    }

                    if (!isGuestPortal) {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Phone SMS Dispatch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                "Automatically open pre-filled Phone SMS composer when scheduling Tasks or sending Messages.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = alertPrefs.autoSmsPromptForTasksAndMessages,
                                        onCheckedChange = { enabled ->
                                            NotificationAlertHelper.setAutoSmsPrompt(context, enabled)
                                            onFeedbackMessage(if (enabled) "Auto Phone SMS prompt enabled" else "Auto Phone SMS prompt disabled")
                                        },
                                        modifier = Modifier.testTag("toggle_auto_sms_switch")
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Screen-Off & Lockscreen Alerts",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Allow Super Service to wake the lockscreen, sound your alert tune, and deliver instant updates even when the phone screen is turned off.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Wake Screen on Alert (Screen Off)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Briefly light up the lockscreen when tasks, requests, messages, or broadcasts arrive while screen is off.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = alertPrefs.wakeScreenOnAlert,
                                        onCheckedChange = { enabled ->
                                            NotificationAlertHelper.setWakeScreenOnAlert(context, enabled)
                                            onFeedbackMessage(
                                                if (enabled) "Screen will wake on incoming alerts"
                                                else "Screen wake on alert disabled"
                                            )
                                        },
                                        modifier = Modifier.testTag("wake_screen_alert_switch")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Real-Time Screen-Off Monitor",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Keep instant background listener active so alerts ring immediately instead of waiting for periodic sync.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = alertPrefs.screenOffSyncServiceEnabled,
                                        onCheckedChange = { enabled ->
                                            if (enabled && !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                NotificationAlertHelper.setScreenOffSyncServiceEnabled(context, true)
                                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                NotificationAlertHelper.setScreenOffSyncServiceEnabled(context, enabled)
                                                onFeedbackMessage(
                                                    if (enabled) "Real-time screen-off alert monitor started"
                                                    else "Real-time screen-off monitor paused (periodic sync active)"
                                                )
                                            }
                                        },
                                        modifier = Modifier.testTag("screen_off_sync_switch")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { NotificationAlertHelper.openAppNotificationSettings(context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("open_lockscreen_notif_settings")
                                    ) {
                                        Icon(Icons.Default.LockOpen, contentDescription = "Lockscreen Notification Settings")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Lockscreen")
                                    }
                                    OutlinedButton(
                                        onClick = { NotificationAlertHelper.openBatteryOptimizationSettings(context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("open_battery_settings")
                                    ) {
                                        Icon(Icons.Default.BatterySaver, contentDescription = "Battery Unrestricted Settings")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Battery")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Pop-Up Over Other Apps & Channel Settings",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Heads-up pop-up alerts and your selected tune work even when the app is closed or screen is off. Ensure 'Show on Lock Screen' and 'Pop on screen' are allowed in Android channel settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { NotificationAlertHelper.openNotificationChannelSettings(context, isTask = true) },
                                        modifier = Modifier.weight(1f).height(48.dp).testTag("open_task_channel_settings")
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Task Pop-Up")
                                    }
                                    OutlinedButton(
                                        onClick = { NotificationAlertHelper.openNotificationChannelSettings(context, isTask = false) },
                                        modifier = Modifier.weight(1f).height(48.dp).testTag("open_msg_channel_settings")
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Msg Pop-Up")
                                    }
                                }
                            }
                        }
                    }

                    if (isAdmin) {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Admin Broadcast Alert",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Send instant heads-up notification to Staff, Clients, Guests, or All Three.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = onOpenBroadcastComposer,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("open_broadcast_composer_from_alerts")
                                    ) {
                                        Icon(Icons.Default.Campaign, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Broadcast")
                                    }
                                }
                            }
                        }
                    }

                    if (!isGuestPortal) {
                        item {
                            NotificationInboxSection(
                                notificationsState = notificationsState,
                                isGuestPortal = false,
                                onMarkNotificationRead = onMarkNotificationRead,
                                onMarkAllRead = onMarkAllRead,
                                onRemoveNotification = onRemoveNotification,
                                onClearReadNotifications = onClearReadNotifications,
                                onNotificationClick = onNotificationClick
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun NotificationInboxSection(
    notificationsState: UiState<List<AppNotification>>,
    isGuestPortal: Boolean,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onRemoveNotification: (AppNotification) -> Unit,
    onClearReadNotifications: () -> Unit,
    onNotificationClick: (AppNotification) -> Unit
) {
    val context = LocalContext.current
    val alerts = (notificationsState as? UiState.Success)?.data ?: emptyList()
    val unreadAlertsCount = alerts.count { !it.isRead }
    val readAlertsCount = alerts.count { it.isRead }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isGuestPortal) {
                        "My Notifications ($unreadAlertsCount unread)"
                    } else {
                        "Recent Notifications ($unreadAlertsCount unread)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isGuestPortal) {
                    Text(
                        "Only personal alerts made for you and official Admin broadcasts. Mark as read to remove.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (unreadAlertsCount > 0) {
                    TextButton(
                        onClick = onMarkAllRead,
                        modifier = Modifier.height(48.dp).testTag("mark_all_notifications_read_button")
                    ) {
                        Text("Mark All Read")
                    }
                }
                if (readAlertsCount > 0) {
                    TextButton(
                        onClick = {
                            alerts.filter { it.isRead }.forEach {
                                NotificationAlertHelper.cancelNotificationForRecord(context, it)
                            }
                            onClearReadNotifications()
                        },
                        modifier = Modifier.height(48.dp).testTag("clear_read_notifications_button")
                    ) {
                        Text("Clear Read", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        UiStateContent(notificationsState) { list ->
            if (list.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "No Notifications Yet",
                    subtitle = if (isGuestPortal) {
                        "Only notifications made for your account and official Admin broadcasts appear here. Mark a notification as read to remove it."
                    } else {
                        "Task assignments, internal messages, broadcasts, and shift alerts appear here."
                    }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { alert ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (!alert.isRead) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    NotificationAlertHelper.cancelNotificationForRecord(context, alert)
                                    onNotificationClick(alert)
                                }
                                .testTag("notification_item_${alert.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (alert.isBroadcast) {
                                    Text(
                                        text = "BROADCAST • ${alert.targetAudience.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        alert.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!alert.isRead) {
                                        TextButton(
                                            onClick = {
                                                NotificationAlertHelper.cancelNotificationForRecord(context, alert)
                                                onMarkNotificationRead(alert.id)
                                            },
                                            modifier = Modifier.height(48.dp).testTag("mark_read_notification_${alert.id}")
                                        ) {
                                            Text("Mark Read")
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                NotificationAlertHelper.cancelNotificationForRecord(context, alert)
                                                onRemoveNotification(alert)
                                            },
                                            modifier = Modifier.height(48.dp).testTag("remove_notification_${alert.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Remove notification",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Remove", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Text(alert.body, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = when {
                                        alert.isRead -> "Read — tap Remove to dismiss"
                                        alert.isBroadcast -> "Unread broadcast — tap card or 'Mark Read' before removing"
                                        else -> "Tap to open ${alert.type.replace("_", " ")} →"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (alert.isRead) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TuneSelectorCard(
    title: String,
    subtitle: String,
    currentTuneTitle: String,
    currentTuneUri: String?,
    testTagPrefix: String,
    onPickSystemRingtone: () -> Unit,
    onPickMobileAudio: () -> Unit,
    onPreview: () -> Unit,
    onResetDefault: () -> Unit,
    onSetSilent: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Active Tune: $currentTuneTitle",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPickSystemRingtone,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("${testTagPrefix}_pick_system")
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Phone Ringtone")
                }
                OutlinedButton(
                    onClick = onPickMobileAudio,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("${testTagPrefix}_pick_audio")
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Choose Audio File")
                }
                OutlinedButton(
                    onClick = onPreview,
                    enabled = currentTuneUri != NotificationAlertHelper.SILENT_SENTINEL,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("${testTagPrefix}_test")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test")
                }
                OutlinedButton(
                    onClick = onResetDefault,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Default")
                }
                OutlinedButton(
                    onClick = onSetSilent,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.NotificationsOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mute")
                }
            }
        }
    }
}
