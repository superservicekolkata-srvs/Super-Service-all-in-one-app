package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.BroadcastAlert
import com.example.data.repository.OperationType
import com.example.data.repository.handleFirestoreError
import com.example.util.NotificationAlertHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ScreenOffMonitorService : Service() {

    private var personalListener: ListenerRegistration? = null
    private var broadcastListener: ListenerRegistration? = null
    private var initialPersonalLoaded = false
    private var initialBroadcastLoaded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationAlertHelper.init(applicationContext)
        startMonitorForeground()
        attachRealtimeListeners()
        return START_STICKY
    }

    private fun startMonitorForeground() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            9001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationAlertHelper.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Super Service Alert Monitor Active")
            .setContentText("Listening for task alerts, messages, and broadcasts.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                9000,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(9000, notification)
        }
    }

    private fun attachRealtimeListeners() {
        detachListeners()
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "No authenticated user; stopping monitor listeners.")
            stopSelf()
            return
        }

        val db = FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))

        personalListener = db.collection("users").document(uid).collection("notifications")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (Firebase.auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "users/$uid/notifications")
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                if (!initialPersonalLoaded) {
                    initialPersonalLoaded = true
                    return@addSnapshotListener
                }
                snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .forEach { change ->
                        val alert = change.document.toObject(
                            AppNotification::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        if (!alert.isRead) {
                            val isTask = alert.type in setOf(
                                "task_assignment",
                                "calendar_reminder",
                                "shift_checkout",
                                "service_request"
                            )
                            if (isTask) {
                                NotificationAlertHelper.showTaskNotification(
                                    context = applicationContext,
                                    title = alert.title,
                                    body = alert.body,
                                    notificationId = alert.id.hashCode()
                                )
                            } else {
                                NotificationAlertHelper.showMessageNotification(
                                    context = applicationContext,
                                    title = alert.title,
                                    body = alert.body,
                                    notificationId = alert.id.hashCode()
                                )
                            }
                        }
                    }
            }

        broadcastListener = db.collection("broadcasts")
            .whereIn("targetAudience", listOf("Staff", "Clients", "Guests", "All Three"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (Firebase.auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "broadcasts")
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                if (!initialBroadcastLoaded) {
                    initialBroadcastLoaded = true
                    return@addSnapshotListener
                }
                snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .forEach { change ->
                        val b = change.document.toObject(
                            BroadcastAlert::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        NotificationAlertHelper.showMessageNotification(
                            context = applicationContext,
                            title = "Broadcast (${b.targetAudience}): ${b.title}",
                            body = b.body,
                            notificationId = b.id.hashCode()
                        )
                    }
            }
    }

    private fun detachListeners() {
        personalListener?.remove()
        personalListener = null
        broadcastListener?.remove()
        broadcastListener = null
        initialPersonalLoaded = false
        initialBroadcastLoaded = false
    }

    override fun onDestroy() {
        detachListeners()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenOffMonitorService"
    }
}
