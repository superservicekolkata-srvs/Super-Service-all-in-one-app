package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.BroadcastAlert
import com.example.data.model.BroadcastState
import com.example.data.model.PortalRole
import com.example.data.model.ServiceMessage
import com.example.data.model.ServiceTask
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SuperServiceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "superservice.kolkata@gmail.com"
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun isBootstrappedAdmin(user: FirebaseUser?): Boolean {
        val email = user?.email ?: return false
        return user.isEmailVerified && email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<UserProfile> {
        val path = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "Authenticated user mismatch" }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) {
                    return Result.success(existing)
                }
            }
            val initialRole = if (isBootstrappedAdmin(user)) PortalRole.ADMIN.raw else PortalRole.GUEST.raw
            val safeName = (user.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotEmpty() }
                ?: "Super Service User").take(80)
            val safeEmail = (user.email?.trim()?.takeIf { it.length >= 3 } ?: "user@example.com").take(200)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "email" to safeEmail,
                "portalRole" to initialRole,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = safeName,
                    email = safeEmail,
                    portalRole = initialRole
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    suspend fun updateProfileDetails(
        userId: String,
        displayName: String,
        phoneNumber: String?
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user profile edit" }
            val cleanName = displayName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty" }
            val cleanPhone = phoneNumber?.trim()?.take(30)?.takeIf { it.isNotEmpty() }
            val updates = buildMap<String, Any> {
                put("displayName", cleanName)
                put("updatedAt", FieldValue.serverTimestamp())
                if (cleanPhone != null) {
                    put("phoneNumber", cleanPhone)
                }
            }
            db.collection("users").document(userId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeTasks(userId: String): Flow<List<ServiceTask>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/tasks"
        emitAll(
            db.collection("users").document(userId).collection("tasks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ServiceTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun createTaskWithAlert(
        userId: String,
        title: String,
        description: String,
        taskType: String,
        dueDateLabel: String,
        contactPhone: String?
    ): Result<String> {
        val path = "users/$userId/tasks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user task write" }
            val cleanTitle = title.trim().take(140)
            require(cleanTitle.isNotEmpty()) { "Task title is required" }
            val cleanDesc = description.trim().take(1000)
            val cleanDue = dueDateLabel.trim().take(80).ifEmpty { "Today" }
            val cleanType = taskType.takeIf {
                it in setOf("task_assignment", "calendar_reminder", "shift_checkout", "service_request")
            } ?: "task_assignment"
            val cleanPhone = contactPhone?.trim()?.take(30)?.takeIf { it.isNotEmpty() }

            val taskRef = db.collection("users").document(userId).collection("tasks").document()
            val notifRef = db.collection("users").document(userId).collection("notifications").document()

            val taskPayload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "description" to cleanDesc,
                "taskType" to cleanType,
                "status" to "pending",
                "dueDateLabel" to cleanDue,
                "contactPhone" to cleanPhone,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val notifPayload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "body" to (cleanDesc.ifEmpty { "Scheduled for $cleanDue" }),
                "type" to cleanType,
                "isRead" to false,
                "isBroadcast" to false,
                "targetAudience" to "Personal",
                "relatedTaskId" to taskRef.id,
                "contactPhone" to cleanPhone,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val batch = db.batch()
            batch.set(taskRef, taskPayload)
            batch.set(notifRef, notifPayload)
            batch.commit().await()
            Result.success(taskRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTaskStatus(userId: String, taskId: String, newStatus: String): Result<Unit> {
        val path = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user task update" }
            require(taskId.isNotBlank()) { "Task ID required" }
            require(newStatus in setOf("pending", "in_progress", "completed")) { "Invalid status" }
            db.collection("users").document(userId).collection("tasks").document(taskId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val path = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user task delete" }
            require(taskId.isNotBlank()) { "Task ID required" }
            db.collection("users").document(userId).collection("tasks").document(taskId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeMessages(userId: String): Flow<List<ServiceMessage>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/messages"
        emitAll(
            db.collection("users").document(userId).collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ServiceMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun sendMessageWithAlert(
        userId: String,
        senderName: String,
        recipientName: String,
        channel: String,
        body: String,
        isUrgent: Boolean,
        recipientPhone: String?
    ): Result<String> {
        val path = "users/$userId/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user message write" }
            val cleanSender = senderName.trim().take(80).ifEmpty { "Super Service" }
            val cleanRecipient = recipientName.trim().take(80)
            require(cleanRecipient.isNotEmpty()) { "Recipient is required" }
            val cleanBody = body.trim().take(1000)
            require(cleanBody.isNotEmpty()) { "Message body is required" }
            val cleanChannel = channel.takeIf { it in setOf("message", "call_log") } ?: "message"
            val cleanPhone = recipientPhone?.trim()?.take(30)?.takeIf { it.isNotEmpty() }

            val msgRef = db.collection("users").document(userId).collection("messages").document()
            val notifRef = db.collection("users").document(userId).collection("notifications").document()

            val msgPayload = mapOf(
                "userId" to uid,
                "senderName" to cleanSender,
                "recipientName" to cleanRecipient,
                "channel" to cleanChannel,
                "body" to cleanBody,
                "isUrgent" to isUrgent,
                "recipientPhone" to cleanPhone,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val notifType = if (cleanChannel == "call_log") "call_log" else "internal_message"
            val notifTitle = if (cleanChannel == "call_log") {
                "Call Log: $cleanRecipient"
            } else {
                "Message to $cleanRecipient"
            }
            val notifPayload = mapOf(
                "userId" to uid,
                "title" to notifTitle.take(140),
                "body" to cleanBody,
                "type" to notifType,
                "isRead" to false,
                "isBroadcast" to false,
                "targetAudience" to "Personal",
                "contactPhone" to cleanPhone,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.set(notifRef, notifPayload)
            batch.commit().await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(userId: String, messageId: String): Result<Unit> {
        val path = "users/$userId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user message delete" }
            require(messageId.isNotBlank()) { "Message ID required" }
            db.collection("users").document(userId).collection("messages").document(messageId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeNotifications(
        userId: String,
        activePortalRole: PortalRole
    ): Flow<List<AppNotification>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val personalPath = "users/$userId/notifications"
        val statesPath = "users/$userId/broadcast_states"
        val broadcastsPath = "broadcasts"

        val personalFlow = db.collection("users").document(userId).collection("notifications")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, personalPath, userId)
                throw error
            }

        val broadcastStatesFlow = db.collection("users").document(userId).collection("broadcast_states")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(BroadcastState::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .associateBy { it.broadcastId }
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, statesPath, userId)
                throw error
            }

        val audiences = if (activePortalRole == PortalRole.ADMIN) {
            listOf("Staff", "Clients", "Guests", "All Three")
        } else {
            listOf(activePortalRole.audienceKey, "All Three")
        }

        val broadcastsFlow = db.collection("broadcasts")
            .whereIn("targetAudience", audiences)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(BroadcastAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                unwrapAndLog(error, OperationType.LIST, broadcastsPath, userId)
                throw error
            }

        emitAll(
            combine(personalFlow, broadcastsFlow, broadcastStatesFlow) { personal, broadcasts, states ->
                val mappedBroadcasts = broadcasts.mapNotNull { b ->
                    val state = states[b.id]
                    if (state?.isDismissed == true) {
                        null
                    } else {
                        AppNotification(
                            id = b.id,
                            userId = userId,
                            title = b.title,
                            body = b.body,
                            type = "broadcast",
                            isRead = state?.isRead ?: false,
                            isBroadcast = true,
                            targetAudience = b.targetAudience,
                            createdAt = b.createdAt,
                            updatedAt = state?.updatedAt ?: b.updatedAt
                        )
                    }
                }
                (personal + mappedBroadcasts)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    suspend fun markNotificationRead(userId: String, alert: AppNotification): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user notification mutation" }
            require(alert.id.isNotBlank()) { "Notification ID required" }

            if (alert.isBroadcast) {
                val docRef = db.collection("users").document(userId)
                    .collection("broadcast_states").document(alert.id)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    docRef.update(
                        mapOf(
                            "isRead" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "userId" to uid,
                            "broadcastId" to alert.id,
                            "isRead" to true,
                            "isDismissed" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                db.collection("users").document(userId)
                    .collection("notifications").document(alert.id)
                    .update(
                        mapOf(
                            "isRead" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val path = if (alert.isBroadcast) {
                "users/$userId/broadcast_states/${alert.id}"
            } else {
                "users/$userId/notifications/${alert.id}"
            }
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(userId: String, alerts: List<AppNotification>): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user notification mutation" }
            val unread = alerts.filter { !it.isRead }
            for (alert in unread) {
                markNotificationRead(userId, alert).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$userId/notifications")
            Result.failure(e)
        }
    }

    suspend fun removeNotification(userId: String, alert: AppNotification): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user notification removal" }
            require(alert.id.isNotBlank()) { "Notification ID required" }

            if (alert.isBroadcast) {
                val docRef = db.collection("users").document(userId)
                    .collection("broadcast_states").document(alert.id)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    docRef.update(
                        mapOf(
                            "isRead" to true,
                            "isDismissed" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "userId" to uid,
                            "broadcastId" to alert.id,
                            "isRead" to true,
                            "isDismissed" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                db.collection("users").document(userId)
                    .collection("notifications").document(alert.id)
                    .delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val path = if (alert.isBroadcast) {
                "users/$userId/broadcast_states/${alert.id}"
            } else {
                "users/$userId/notifications/${alert.id}"
            }
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearReadNotifications(userId: String, alerts: List<AppNotification>): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user notification clear" }
            val readAlerts = alerts.filter { it.isRead }
            for (alert in readAlerts) {
                removeNotification(userId, alert).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId/notifications")
            Result.failure(e)
        }
    }

    suspend fun publishBroadcast(
        title: String,
        body: String,
        targetAudience: String
    ): Result<String> {
        val path = "broadcasts"
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("User must be signed in before publishing broadcast.")
            val cleanTitle = title.trim().take(140)
            val cleanBody = body.trim().take(1000)
            require(cleanTitle.isNotEmpty()) { "Broadcast headline is required" }
            require(cleanBody.isNotEmpty()) { "Broadcast message is required" }
            val audience = targetAudience.takeIf {
                it in setOf("Staff", "Clients", "Guests", "All Three")
            } ?: "All Three"
            val docRef = db.collection("broadcasts").document()
            val payload = mapOf(
                "authorId" to user.uid,
                "authorEmail" to (user.email ?: BOOTSTRAPPED_ADMIN_EMAIL).take(200),
                "title" to cleanTitle,
                "body" to cleanBody,
                "targetAudience" to audience,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun unwrapAndLog(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
