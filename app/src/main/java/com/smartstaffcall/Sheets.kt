package com.smartstaffcall

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryRow(c: CallRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${c.staffName} (${c.staffId})", fontWeight = FontWeight.Bold)
                    if (c.staffPost.isNotBlank()) Text(c.staffPost, fontSize = 12.sp, color = SoftGray)
                }
                StatusChip(c.status)
            }
            Text("Created: ${fmtTime(c.createdAt)}", fontSize = 13.sp)
            Text("Received: ${fmtTime(c.receivedAt)}", fontSize = 13.sp)
            Text("On the way: ${fmtTime(c.onTheWayAt)}", fontSize = 13.sp)
            Text("Completed: ${fmtTime(c.completedAt)}", fontSize = 13.sp)
            if (c.endedAt != null) Text("Ended (${c.status.label}): ${fmtTime(c.endedAt)}", fontSize = 13.sp)
            Text("Response time: ${c.responseMs?.let { fmtDur(it) } ?: "n/a"}", fontSize = 13.sp)
        }
    }
}

@Composable
fun PresenceRow(p: PresenceRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${p.staffName} (${p.staffId}) - ${if (p.type == "LEAVE") "On leave" else "Away"}", fontWeight = FontWeight.Bold)
            Text("Reason: ${p.reason}", fontSize = 13.sp)
            Text("From ${fmtTime(p.startedAt)} to ${if (p.endedAt == null) "now" else fmtTime(p.endedAt)}", fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(vm: AppViewModel, s: Staff, onClose: () -> Unit, onCalled: (String) -> Unit) {
    var mode by remember { mutableStateOf<Presence?>(null) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val visual = vm.visualOf(s, System.currentTimeMillis())
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(s.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                s.post.label + "  |  " + visual.label + if (s.presence != Presence.PRESENT) ": ${s.presenceReason}" else "",
                color = SoftGray
            )
            Button(
                onClick = { vm.createCall(s)?.let(onCalled) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (s.presence == Presence.PRESENT) "Call now (simulated)" else "Call anyway (simulated)", fontSize = 18.sp) }

            if (mode == null) {
                if (s.presence == Presence.PRESENT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { mode = Presence.AWAY }, modifier = Modifier.weight(1f)) { Text("Away 1 hour") }
                        OutlinedButton(onClick = { mode = Presence.LEAVE }, modifier = Modifier.weight(1f)) { Text("On leave") }
                    }
                } else {
                    OutlinedButton(
                        onClick = { vm.setPresence(s.id, Presence.PRESENT, ""); onClose() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Make available") }
                }
            } else {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text(if (mode == Presence.AWAY) "Reason for being away" else "Leave reason") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { mode = null; error = null }) { Text("Back") }
                    Button(onClick = {
                        val chosen = mode
                        if (chosen != null) {
                            val e = vm.setPresence(s.id, chosen, reason)
                            if (e == null) onClose() else error = e
                        }
                    }) { Text("Save") }
                }
            }
            Text("Tip: hold a staff button for 5 seconds to see responsibilities and current work.", fontSize = 12.sp, color = SoftGray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(s: Staff, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Palette.color(s.colorIndex)))
                Spacer(Modifier.width(8.dp))
                Text(s.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(s.post.label + "  |  ID " + s.id, color = SoftGray)
            if (s.presence != Presence.PRESENT) {
                Text(
                    (if (s.presence == Presence.AWAY) "Away: " else "On leave: ") + s.presenceReason,
                    fontWeight = FontWeight.SemiBold
                )
            }
            DetailBlock("Responsibilities", s.responsibilities)
            DetailBlock("Current work", s.currentWork)
            Text("Change these in Manage staff.", fontSize = 12.sp, color = SoftGray)
        }
    }
}

@Composable
fun DetailBlock(title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppBg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(if (text.isBlank()) "Not added yet." else text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSheet(call: CallRecord, vm: AppViewModel, onClose: () -> Unit) {
    val next = call.status.allowedNext()
    val staffActions = next.filter { it == CallStatus.RECEIVED || it == CallStatus.ON_THE_WAY || it == CallStatus.COMPLETED }
    val adminActions = next.filter { it == CallStatus.CANCELLED || it == CallStatus.MISSED || it == CallStatus.NO_RESPONSE }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("${call.staffName} (${call.staffId})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            StatusChip(call.status)
            Text("Created: ${fmtTime(call.createdAt)}", color = SoftGray)
            if (call.status == CallStatus.PENDING) {
                Text("Staff must respond within 10 seconds or the call becomes No response.", fontSize = 12.sp, color = SoftGray)
            }
            if (staffActions.isNotEmpty()) {
                Text("Staff response (simulated on this phone):", fontWeight = FontWeight.Bold)
                staffActions.forEach {
                    Button(
                        onClick = { vm.updateStatus(call.callId, it) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(it.label, fontSize = 17.sp) }
                }
            }
            if (adminActions.isNotEmpty()) {
                Text("Administrator actions:", fontWeight = FontWeight.Bold)
                adminActions.forEach {
                    OutlinedButton(
                        onClick = { vm.updateStatus(call.callId, it) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (it == CallStatus.CANCELLED) "Cancel call" else "Mark ${it.label}") }
                }
            }
            if (next.isEmpty()) Text("This call is finished.")
        }
    }
}

// END OF FILE