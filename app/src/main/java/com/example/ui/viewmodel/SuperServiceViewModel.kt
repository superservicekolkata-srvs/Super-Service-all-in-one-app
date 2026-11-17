package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.PortalRole
import com.example.data.model.ServiceMessage
import com.example.data.model.ServiceTask
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.SuperServiceRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SmsDispatchEvent {
    data class OpenSms(val phone: String?, val body: String) : SmsDispatchEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class SuperServiceViewModel(
    private val repository: SuperServiceRepository,
    private val currentUserId: String,
    private val initialIsBootstrappedAdmin: Boolean
) : ViewModel() {

    private val _selectedPortalPreview = MutableStateFlow(
        if (initialIsBootstrappedAdmin) PortalRole.ADMIN else PortalRole.GUEST
    )
    val selectedPortalPreview: StateFlow<PortalRole> = _selectedPortalPreview.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val feedbackEvents: SharedFlow<String> = _feedbackEvents.asSharedFlow()

    private val _smsDispatchEvents = MutableSharedFlow<SmsDispatchEvent>(extraBufferCapacity = 4)
    val smsDispatchEvents: SharedFlow<SmsDispatchEvent> = _smsDispatchEvents.asSharedFlow()

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            unwrapFirestoreFlowError(error, "Failed to load profile") { msg ->
                emit(UiState.Error(msg))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val tasksState: StateFlow<UiState<List<ServiceTask>>> = repository.observeTasks(currentUserId)
        .map<List<ServiceTask>, UiState<List<ServiceTask>>> { UiState.Success(it) }
        .catch { error ->
            unwrapFirestoreFlowError(error, "Failed to load tasks") { msg ->
                emit(UiState.Error(msg))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<ServiceMessage>>> = repository.observeMessages(currentUserId)
        .map<List<ServiceMessage>, UiState<List<ServiceMessage>>> { UiState.Success(it) }
        .catch { error ->
            unwrapFirestoreFlowError(error, "Failed to load messages") { msg ->
                emit(UiState.Error(msg))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val notificationsState: StateFlow<UiState<List<AppNotification>>> = _selectedPortalPreview
        .flatMapLatest { portalRole ->
            repository.observeNotifications(currentUserId, portalRole)
                .map<List<AppNotification>, UiState<List<AppNotification>>> { UiState.Success(it) }
                .catch { error ->
                    unwrapFirestoreFlowError(error, "Failed to load notifications") { msg ->
                        emit(UiState.Error(msg))
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user != null && user.uid == currentUserId) {
                repository.ensureUserProfile(user).onSuccess { profile ->
                    val assigned = PortalRole.fromRaw(profile.portalRole)
                    _selectedPortalPreview.value = if (initialIsBootstrappedAdmin) {
                        PortalRole.ADMIN
                    } else {
                        assigned
                    }
                }
            }
        }
    }

    fun selectPortalView(role: PortalRole) {
        _selectedPortalPreview.value = role
    }

    fun emitFeedback(message: String) {
        _feedbackEvents.tryEmit(message)
    }

    fun updateProfile(displayName: String, phoneNumber: String?) {
        viewModelScope.launch {
            repository.updateProfileDetails(currentUserId, displayName, phoneNumber)
                .onSuccess { _feedbackEvents.tryEmit("Profile updated") }
                .onFailure { _feedbackEvents.tryEmit(it.localizedMessage ?: "Failed to update profile") }
        }
    }

    fun createTask(
        title: String,
        description: String,
        taskType: String,
        dueDateLabel: String,
        contactPhone: String?,
        autoSmsEnabled: Boolean
    ) {
        viewModelScope.launch {
            repository.createTaskWithAlert(
                userId = currentUserId,
                title = title,
                description = description,
                taskType = taskType,
                dueDateLabel = dueDateLabel,
                contactPhone = contactPhone
            ).onSuccess {
                _feedbackEvents.tryEmit("Task scheduled & alert created")
                if (autoSmsEnabled) {
                    val smsText = "Super Service Task: $title ($dueDateLabel). ${description.trim()}"
                    _smsDispatchEvents.tryEmit(SmsDispatchEvent.OpenSms(contactPhone, smsText.trim()))
                }
            }.onFailure {
                _feedbackEvents.tryEmit(it.localizedMessage ?: "Unable to schedule task")
            }
        }
    }

    fun updateTaskStatus(taskId: String, newStatus: String) {
        viewModelScope.launch {
            repository.updateTaskStatus(currentUserId, taskId, newStatus)
                .onSuccess { _feedbackEvents.tryEmit("Task marked $newStatus") }
                .onFailure { _feedbackEvents.tryEmit(it.localizedMessage ?: "Unable to update task") }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(currentUserId, taskId)
                .onSuccess { _feedbackEvents.tryEmit("Task removed") }
                .onFailure { _feedbackEvents.tryEmit(it.localizedMessage ?: "Unable to remove task") }
        }
    }

    fun sendMessage(
        senderName: String,
        recipientName: String,
        channel: String,
        body: String,
        isUrgent: Boolean,
        recipientPhone: String?,
        autoSmsEnabled: Boolean
    ) {
        viewModelScope.launch {
            repository.sendMessageWithAlert(
                userId = currentUserId,
                senderName = senderName,
                recipientName = recipientName,
                channel = channel,
                body = body,
                isUrgent = isUrgent,
                recipientPhone = recipientPhone
            ).onSuccess {
                _feedbackEvents.tryEmit(if (channel == "call_log") "Call log saved & alert sent" else "Message sent & alert logged")
                if (autoSmsEnabled) {
                    val smsText = "Super Service (${if (channel == "call_log") "Call Follow-up" else "Message"}): $body"
                    _smsDispatchEvents.tryEmit(SmsDispatchEvent.OpenSms(recipientPhone, smsText))
                }
            }.onFailure {
                _feedbackEvents.tryEmit(it.localizedMessage ?: "Unable to send message")
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(currentUserId, messageId)
                .onSuccess { _feedbackEvents.tryEmit("Entry deleted") }
                .onFailure { _feedbackEvents.tryEmit(it.localizedMessage ?: "Unable to delete entry") }
        }
    }

    fun markNotificationRead(alertId: String) {
        val current = (notificationsState.value as? UiState.Success)?.data ?: return
        val target = current.firstOrNull { it.id == alertId } ?: return
        viewModelScope.launch {
            repository.markNotificationRead(currentUserId, target)
                .onFailure { _feedbackEvents.tryEmit(it.localizedMessage ?: "Failed to mark notification read") }
        }
    }

    fun markAllNotificationsRead() {
        val current = (notificationsState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.markAllNotificationsRead(currentUserId, current)
                .onSuccess { _feedbackEvents.tryEmit("All notifications marked read") }
                .onFailure { _feedbackEvents.tryEmit(it.localizedMessage ?: "Failed to mark all read") }
        }
    }

    fun removeNotification(alert: AppNotification) {
        viewModelScope.launch {
            repository.removeNotification(currentUserId, alert)
                .onSuccess { _feedbackEvents.tryEmit("Notification removed") }
                .onFailure { _feedbackEvents.tryEmit(it.localizedMessage ?: "Failed to remove notification") }
        }
    }

    fun clearReadNotifications() {
        val current = (notificationsState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.clearReadNotifications(currentUserId, current)
                .onSuccess { _feedbackEvents.tryEmit("Read notifications cleared") }
                .onFailure { _feedbackEvents.tryEmit(it.localizedMessage ?: "Failed to clear notifications") }
        }
    }

    fun publishBroadcast(title: String, body: String, targetAudience: String) {
        viewModelScope.launch {
            repository.publishBroadcast(title, body, targetAudience)
                .onSuccess { _feedbackEvents.tryEmit("Broadcast dispatched to $targetAudience") }
                .onFailure { _feedbackEvents.tryEmit(it.localizedMessage ?: "Unable to publish broadcast") }
        }
    }

    private suspend inline fun unwrapFirestoreFlowError(
        error: Throwable,
        fallbackMessage: String,
        emitError: suspend (String) -> Unit
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, fallbackMessage, rootError)
        emitError(rootError.message ?: fallbackMessage)
    }

    private companion object {
        const val TAG = "SuperServiceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
