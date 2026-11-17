package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.PortalRole
import com.example.data.model.ServiceMessage
import com.example.data.model.ServiceTask
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PortalRoleBadge
import com.example.ui.components.UiStateContent
import com.example.ui.viewmodel.SmsDispatchEvent
import com.example.ui.viewmodel.SuperServiceViewModel
import com.example.util.NotificationAlertHelper

private enum class MainTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Overview", Icons.Default.Dashboard),
    TASKS("Tasks", Icons.Default.TaskAlt),
    MESSAGES("Messages", Icons.Default.Email),
    ALERTS("Alerts & Tunes", Icons.Default.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperServiceMainScreen(
    viewModel: SuperServiceViewModel,
    isBootstrappedAdmin: Boolean,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val activePortal by viewModel.selectedPortalPreview.collectAsStateWithLifecycle()
    val alertPrefs by NotificationAlertHelper.prefsFlow.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var showAlertsAndTunesDialog by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var showComposeMessageDialog by remember { mutableStateOf(false) }

    val profile = (profileState as? UiState.Success)?.data
    val isAdminAccount = isBootstrappedAdmin ||
        profile?.portalRole.equals(PortalRole.ADMIN.raw, ignoreCase = true)
    val isGuestPortal = activePortal == PortalRole.GUEST

    val alerts = (notificationsState as? UiState.Success)?.data ?: emptyList()
    val unreadCount = alerts.count { !it.isRead }

    LaunchedEffect(Unit) {
        NotificationAlertHelper.init(context)
        NotificationAlertHelper.syncScreenOffMonitorService(context)
    }

    LaunchedEffect(Unit) {
        viewModel.feedbackEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.smsDispatchEvents.collect { event ->
            when (event) {
                is SmsDispatchEvent.OpenSms -> {
                    val launched = NotificationAlertHelper.launchSmsComposer(
                        context = context,
                        phoneNumber = event.phone,
                        body = event.body
                    )
                    if (!launched) {
                        snackbarHostState.showSnackbar("No SMS app available on this device.")
                    }
                }
            }
        }
    }

    val handleNotificationClick: (AppNotification) -> Unit = { alert ->
        if (!alert.isRead) {
            viewModel.markNotificationRead(alert.id)
        }
        showAlertsAndTunesDialog = false
        currentTab = when (alert.type) {
            "task_assignment", "calendar_reminder", "shift_checkout", "service_request" -> MainTab.TASKS
            "internal_message", "call_log" -> MainTab.MESSAGES
            else -> MainTab.DASHBOARD
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.super_service_logo),
                                contentDescription = "Super Service Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column {
                                Text(
                                    text = "Super Service",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activePortal.label} Portal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAlertsAndTunesDialog = true },
                            modifier = Modifier.testTag("top_bar_alerts_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(unreadCount.coerceAtMost(99).toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Open Notifications and Alert Tunes"
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                signOutFromSuperService(
                                    context = context,
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sign Out"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = {
                                    if (tab == MainTab.ALERTS && unreadCount > 0) {
                                        BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                                            Icon(tab.icon, contentDescription = tab.label)
                                        }
                                    } else {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                when (currentTab) {
                    MainTab.TASKS -> {
                        if (!isGuestPortal) {
                            FloatingActionButton(
                                onClick = { showCreateTaskDialog = true },
                                modifier = Modifier.testTag("fab_create_task")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Schedule Task")
                            }
                        }
                    }
                    MainTab.MESSAGES -> {
                        if (!isGuestPortal) {
                            FloatingActionButton(
                                onClick = { showComposeMessageDialog = true },
                                modifier = Modifier.testTag("fab_compose_message")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Compose Message")
                            }
                        }
                    }
                    else -> {}
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        MainTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                        when (currentTab) {
                            MainTab.DASHBOARD -> DashboardTab(
                                profile = profile,
                                userEmail = userEmail,
                                activePortal = activePortal,
                                isAdminAccount = isAdminAccount,
                                tasksState = tasksState,
                                messagesState = messagesState,
                                notificationsState = notificationsState,
                                onSelectPortal = viewModel::selectPortalView,
                                onOpenAlertsDialog = { showAlertsAndTunesDialog = true },
                                onOpenBroadcast = { showBroadcastDialog = true },
                                onOpenCreateTask = { showCreateTaskDialog = true },
                                onOpenComposeMessage = { showComposeMessageDialog = true },
                                onMarkRead = viewModel::markNotificationRead
                            )
                            MainTab.TASKS -> TasksTab(
                                tasksState = tasksState,
                                isGuestPortal = isGuestPortal,
                                onUpdateStatus = viewModel::updateTaskStatus,
                                onDeleteTask = viewModel::deleteTask,
                                onSendSms = { phone, body ->
                                    NotificationAlertHelper.launchSmsComposer(context, phone, body)
                                },
                                onCreateTaskClick = { showCreateTaskDialog = true }
                            )
                            MainTab.MESSAGES -> MessagesTab(
                                messagesState = messagesState,
                                isGuestPortal = isGuestPortal,
                                onDeleteMessage = viewModel::deleteMessage,
                                onSendSms = { phone, body ->
                                    NotificationAlertHelper.launchSmsComposer(context, phone, body)
                                },
                                onComposeClick = { showComposeMessageDialog = true }
                            )
                            MainTab.ALERTS -> AlertsHubTab(
                                alertPrefsTitleTask = alertPrefs.taskTuneTitle,
                                alertPrefsTitleMsg = alertPrefs.messageTuneTitle,
                                unreadCount = unreadCount,
                                isAdmin = isAdminAccount && !isGuestPortal,
                                isGuestPortal = isGuestPortal,
                                onOpenAlertsAndTunesDialog = { showAlertsAndTunesDialog = true },
                                onOpenBroadcastDialog = { showBroadcastDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAlertsAndTunesDialog) {
        NotificationAlertsAndTunesDialog(
            notificationsState = notificationsState,
            isAdmin = isAdminAccount && !isGuestPortal,
            isGuestPortal = isGuestPortal,
            onOpenBroadcastComposer = {
                showAlertsAndTunesDialog = false
                showBroadcastDialog = true
            },
            onMarkNotificationRead = viewModel::markNotificationRead,
            onMarkAllRead = viewModel::markAllNotificationsRead,
            onRemoveNotification = viewModel::removeNotification,
            onClearReadNotifications = viewModel::clearReadNotifications,
            onNotificationClick = handleNotificationClick,
            onFeedbackMessage = viewModel::emitFeedback,
            onDismiss = { showAlertsAndTunesDialog = false }
        )
    }

    if (showBroadcastDialog) {
        AdminBroadcastDialog(
            onPublish = viewModel::publishBroadcast,
            onDismiss = { showBroadcastDialog = false }
        )
    }

    if (showCreateTaskDialog) {
        CreateTaskDialog(
            onCreateTask = { title, desc, type, due, phone ->
                viewModel.createTask(
                    title = title,
                    description = desc,
                    taskType = type,
                    dueDateLabel = due,
                    contactPhone = phone,
                    autoSmsEnabled = alertPrefs.autoSmsPromptForTasksAndMessages
                )
            },
            onDismiss = { showCreateTaskDialog = false }
        )
    }

    if (showComposeMessageDialog) {
        ComposeMessageDialog(
            defaultSenderName = profile?.displayName ?: userEmail.substringBefore("@"),
            onSendMessage = { sender, recipient, channel, body, urgent, phone ->
                viewModel.sendMessage(
                    senderName = sender,
                    recipientName = recipient,
                    channel = channel,
                    body = body,
                    isUrgent = urgent,
                    recipientPhone = phone,
                    autoSmsEnabled = alertPrefs.autoSmsPromptForTasksAndMessages
                )
            },
            onDismiss = { showComposeMessageDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardTab(
    profile: UserProfile?,
    userEmail: String,
    activePortal: PortalRole,
    isAdminAccount: Boolean,
    tasksState: UiState<List<ServiceTask>>,
    messagesState: UiState<List<ServiceMessage>>,
    notificationsState: UiState<List<AppNotification>>,
    onSelectPortal: (PortalRole) -> Unit,
    onOpenAlertsDialog: () -> Unit,
    onOpenBroadcast: () -> Unit,
    onOpenCreateTask: () -> Unit,
    onOpenComposeMessage: () -> Unit,
    onMarkRead: (String) -> Unit
) {
    val tasks = (tasksState as? UiState.Success)?.data ?: emptyList()
    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
    val alerts = (notificationsState as? UiState.Success)?.data ?: emptyList()
    val unreadAlerts = alerts.filter { !it.isRead }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                )
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Welcome, ${profile?.displayName ?: userEmail.substringBefore("@")}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PortalRoleBadge(role = activePortal)
                    }

                    Text(
                        text = if (isAdminAccount) {
                            "Portal Preview & Role Mode"
                        } else {
                            "Switch View (Guest Notification Filter)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val availablePortals = if (isAdminAccount) {
                            PortalRole.entries
                        } else {
                            listOf(PortalRole.STAFF, PortalRole.CLIENT, PortalRole.GUEST)
                        }
                        availablePortals.forEach { role ->
                            FilterChip(
                                selected = activePortal == role,
                                onClick = { onSelectPortal(role) },
                                label = { Text(role.label) },
                                modifier = Modifier.testTag("portal_chip_${role.raw}")
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Unread Alerts",
                    value = unreadAlerts.size.toString(),
                    modifier = Modifier.weight(1f).clickable { onOpenAlertsDialog() }
                )
                MetricCard(
                    title = "Active Tasks",
                    value = tasks.count { it.status != "completed" }.toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Messages",
                    value = messages.size.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenAlertsDialog,
                    modifier = Modifier.height(48.dp).testTag("dashboard_open_alerts_dialog")
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alert Tunes & Inbox")
                }
                if (isAdminAccount && activePortal != PortalRole.GUEST) {
                    Button(
                        onClick = onOpenBroadcast,
                        modifier = Modifier.height(48.dp).testTag("dashboard_open_broadcast")
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Broadcast")
                    }
                }
                if (activePortal != PortalRole.GUEST) {
                    OutlinedButton(
                        onClick = onOpenCreateTask,
                        modifier = Modifier.height(48.dp).testTag("dashboard_new_task")
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Task")
                    }
                    OutlinedButton(
                        onClick = onOpenComposeMessage,
                        modifier = Modifier.height(48.dp).testTag("dashboard_new_msg")
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Message")
                    }
                }
            }
        }

        item {
            Text(
                text = "Unread Alerts Feed (${unreadAlerts.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            UiStateContent(notificationsState) { list ->
                val unread = list.filter { !it.isRead }.take(5)
                if (unread.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Default.CheckCircle,
                        title = "All Caught Up",
                        subtitle = "No unread alerts in your ${activePortal.label} inbox."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        unread.forEach { alert ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (alert.isBroadcast) {
                                            Text(
                                                text = "BROADCAST • ${alert.targetAudience.uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(alert.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(alert.body, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(
                                        onClick = { onMarkRead(alert.id) },
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Mark Read")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TasksTab(
    tasksState: UiState<List<ServiceTask>>,
    isGuestPortal: Boolean,
    onUpdateStatus: (String, String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onSendSms: (String?, String) -> Unit,
    onCreateTaskClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tasks, Shifts & Reminders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!isGuestPortal) {
                    TextButton(onClick = onCreateTaskClick, modifier = Modifier.height(48.dp)) {
                        Text("+ Schedule")
                    }
                }
            }
        }

        item {
            UiStateContent(tasksState) { list ->
                if (list.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Default.TaskAlt,
                        title = "No Scheduled Tasks",
                        subtitle = if (isGuestPortal) {
                            "Guest Portal shows read-only status. Switch to Staff or Admin to create tasks."
                        } else {
                            "Tap + to schedule a task assignment, calendar reminder, or shift check-out."
                        }
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.forEach { task ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth().testTag("task_card_${task.id}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${task.taskType.replace("_", " ").uppercase()} • ${task.dueDateLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = when (task.status) {
                                                "completed" -> MaterialTheme.colorScheme.tertiaryContainer
                                                "in_progress" -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = task.status.replace("_", " ").uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (task.description.isNotEmpty()) {
                                        Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!isGuestPortal) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (task.status != "completed") {
                                                    val next = if (task.status == "pending") "in_progress" else "completed"
                                                    OutlinedButton(
                                                        onClick = { onUpdateStatus(task.id, next) },
                                                        modifier = Modifier.height(48.dp)
                                                    ) {
                                                        Text(if (next == "in_progress") "Start" else "Complete")
                                                    }
                                                }
                                                if (!task.contactPhone.isNullOrBlank()) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            onSendSms(task.contactPhone, "Super Service Task: ${task.title} (${task.dueDateLabel})")
                                                        },
                                                        modifier = Modifier.height(48.dp)
                                                    ) {
                                                        Icon(Icons.Default.Sms, contentDescription = "Send SMS")
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("SMS")
                                                    }
                                                }
                                            }
                                            IconButton(onClick = { onDeleteTask(task.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Task", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesTab(
    messagesState: UiState<List<ServiceMessage>>,
    isGuestPortal: Boolean,
    onDeleteMessage: (String) -> Unit,
    onSendSms: (String?, String) -> Unit,
    onComposeClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Messages & Call Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!isGuestPortal) {
                    TextButton(onClick = onComposeClick, modifier = Modifier.height(48.dp)) {
                        Text("+ Compose")
                    }
                }
            }
        }

        item {
            UiStateContent(messagesState) { list ->
                if (list.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Default.Email,
                        title = "No Messages or Call Logs",
                        subtitle = "Internal Staff/Admin/Client dispatches and call logs appear here."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.forEach { msg ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (msg.isUrgent) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(
                                                imageVector = if (msg.channel == "call_log") Icons.Default.Call else Icons.Default.Email,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "To: ${msg.recipientName}" + if (msg.isUrgent) " • URGENT" else "",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        IconButton(onClick = { onDeleteMessage(msg.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Message", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                                    if (!msg.recipientPhone.isNullOrBlank()) {
                                        OutlinedButton(
                                            onClick = { onSendSms(msg.recipientPhone, msg.body) },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Icon(Icons.Default.Sms, contentDescription = "Open SMS")
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Send Phone SMS (${msg.recipientPhone})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsHubTab(
    alertPrefsTitleTask: String,
    alertPrefsTitleMsg: String,
    unreadCount: Int,
    isAdmin: Boolean,
    isGuestPortal: Boolean,
    onOpenAlertsAndTunesDialog: () -> Unit,
    onOpenBroadcastDialog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isGuestPortal) "My Notifications & Alert Tunes" else "Alert Tunes, Lockscreen & Inbox",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Unread alerts: $unreadCount • Task Tune: $alertPrefsTitleTask • Message Tune: $alertPrefsTitleMsg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Button(
                        onClick = onOpenAlertsAndTunesDialog,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_full_alerts_tunes_dialog")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Notification Alerts & Tunes")
                    }
                    if (isAdmin) {
                        OutlinedButton(
                            onClick = onOpenBroadcastDialog,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("alerts_tab_broadcast_button")
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compose Admin Broadcast")
                        }
                    }
                }
            }
        }
    }
}
