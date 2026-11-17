package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminBroadcastDialog(
    onPublish: (title: String, body: String, audience: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedAudience by remember { mutableStateOf("All Three") }
    val audiences = listOf("Staff", "Clients", "Guests", "All Three")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish Admin Broadcast", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Deliver an instant heads-up notification across Super Service portals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Target Audience", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    audiences.forEach { aud ->
                        FilterChip(
                            selected = selectedAudience == aud,
                            onClick = { selectedAudience = aud },
                            label = { Text(aud) },
                            modifier = Modifier.testTag("audience_chip_${aud.lowercase().replace(" ", "_")}")
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 140) title = it },
                    label = { Text("Broadcast Headline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("broadcast_title_input")
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { if (it.length <= 1000) body = it },
                    label = { Text("Broadcast Message") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("broadcast_body_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPublish(title.trim(), body.trim(), selectedAudience)
                    onDismiss()
                },
                enabled = title.trim().isNotEmpty() && body.trim().isNotEmpty(),
                modifier = Modifier.height(48.dp).testTag("publish_broadcast_button")
            ) {
                Text("Send Broadcast")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTaskDialog(
    onCreateTask: (
        title: String,
        description: String,
        taskType: String,
        dueDateLabel: String,
        contactPhone: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDateLabel by remember { mutableStateOf("Today, 5:00 PM") }
    var contactPhone by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("task_assignment") }

    val types = listOf(
        "task_assignment" to "Task",
        "calendar_reminder" to "Calendar",
        "shift_checkout" to "Shift Out",
        "service_request" to "Request"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Task or Shift Alert", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    types.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 140) title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 1000) description = it },
                    label = { Text("Details") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("task_desc_input")
                )
                OutlinedTextField(
                    value = dueDateLabel,
                    onValueChange = { if (it.length <= 80) dueDateLabel = it },
                    label = { Text("Schedule / Shift Time") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task_due_input")
                )
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { if (it.length <= 30) contactPhone = it },
                    label = { Text("Contact Phone (Optional for SMS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("task_phone_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreateTask(
                        title.trim(),
                        description.trim(),
                        selectedType,
                        dueDateLabel.trim(),
                        contactPhone.trim().takeIf { it.isNotEmpty() }
                    )
                    onDismiss()
                },
                enabled = title.trim().isNotEmpty(),
                modifier = Modifier.height(48.dp).testTag("confirm_create_task_button")
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun ComposeMessageDialog(
    defaultSenderName: String,
    onSendMessage: (
        senderName: String,
        recipientName: String,
        channel: String,
        body: String,
        isUrgent: Boolean,
        recipientPhone: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("message") }
    var isUrgent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Internal Message / Call Log", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = channel == "message",
                        onClick = { channel = "message" },
                        label = { Text("Message") }
                    )
                    FilterChip(
                        selected = channel == "call_log",
                        onClick = { channel = "call_log" },
                        label = { Text("Call Log") }
                    )
                }
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { if (it.length <= 80) recipientName = it },
                    label = { Text("Recipient (Staff / Client / Desk)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("msg_recipient_input")
                )
                OutlinedTextField(
                    value = recipientPhone,
                    onValueChange = { if (it.length <= 30) recipientPhone = it },
                    label = { Text("Phone Number (Optional for SMS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("msg_phone_input")
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { if (it.length <= 1000) body = it },
                    label = { Text(if (channel == "call_log") "Call Summary" else "Message") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("msg_body_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mark Urgent", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = isUrgent, onCheckedChange = { isUrgent = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSendMessage(
                        defaultSenderName,
                        recipientName.trim(),
                        channel,
                        body.trim(),
                        isUrgent,
                        recipientPhone.trim().takeIf { it.isNotEmpty() }
                    )
                    onDismiss()
                },
                enabled = recipientName.trim().isNotEmpty() && body.trim().isNotEmpty(),
                modifier = Modifier.height(48.dp).testTag("confirm_send_message_button")
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}
