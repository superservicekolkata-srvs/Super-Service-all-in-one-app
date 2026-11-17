package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class PortalRole(val raw: String, val label: String, val audienceKey: String) {
    ADMIN("admin", "Admin", "All Three"),
    STAFF("staff", "Staff", "Staff"),
    CLIENT("client", "Client", "Clients"),
    GUEST("guest", "Guest", "Guests");

    companion object {
        fun fromRaw(raw: String?): PortalRole =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: GUEST
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val portalRole: String = PortalRole.GUEST.raw,
    val phoneNumber: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ServiceTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val taskType: String = "task_assignment",
    val status: String = "pending",
    val dueDateLabel: String = "",
    val contactPhone: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ServiceMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderName: String = "",
    val recipientName: String = "",
    val channel: String = "message",
    val body: String = "",
    @get:PropertyName("isUrgent") @PropertyName("isUrgent")
    val isUrgent: Boolean = false,
    val recipientPhone: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "task_assignment",
    @get:PropertyName("isRead") @PropertyName("isRead")
    val isRead: Boolean = false,
    @get:PropertyName("isBroadcast") @PropertyName("isBroadcast")
    val isBroadcast: Boolean = false,
    val targetAudience: String = "Personal",
    val relatedTaskId: String? = null,
    val contactPhone: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BroadcastAlert(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorEmail: String = "",
    val title: String = "",
    val body: String = "",
    val targetAudience: String = "All Three",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BroadcastState(
    @DocumentId val id: String = "",
    val userId: String = "",
    val broadcastId: String = "",
    @get:PropertyName("isRead") @PropertyName("isRead")
    val isRead: Boolean = false,
    @get:PropertyName("isDismissed") @PropertyName("isDismissed")
    val isDismissed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
