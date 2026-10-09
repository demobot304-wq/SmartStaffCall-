package com.smartstaffcall

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtTime(t: Long?): String =
    if (t == null) "-" else SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(t))

fun fmtDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}

@Composable
fun StatusChip(status: CallStatus) {
    Text(
        status.label,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(status.color).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel, openCallId: String?, onOpenHandled: () -> Unit) {
    var showManage by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedCallId by remember { mutableStateOf<String?>(null) }
    var confirmStaff by remember { mutableStateOf<Staff?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    LaunchedEffect(openCallId) {
        if (openCallId != null) {
            if (vm.callById(openCallId) != null) selectedCallId = openCallId
            onOpenHandled()
        }
    }

    if (showManage) {
        BackHandler { showManage = false }
        ManageScreen(vm, snackbar) { showManage = false }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Staff Call") },
                actions = { TextButton(onClick = { showManage = true }) { Text("Manage staff") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        val filtered = vm.staff.filter { it.name.contains(query.trim(), ignoreCase = true) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad).fillMaxSize()
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "PROTOTYPE: calls and notifications are simulated on this phone only. Nothing is sent to any other device.",
                        fontSize = 12.sp,
                        color = Color(0xFF7A4B00),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3D6)).padding(10.dp)
                    )
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text("Search staff by name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (filtered.isEmpty()) Text("No staff match your search.")
                }
            }
            items(filtered, key = { "s_" + it.id }) { s ->
                StaffButton(s, vm.activeCall(s.id)) {
                    val active = vm.activeCall(s.id)
                    if (active != null) selectedCallId = active.callId else confirmStaff = s
                }
            }
            item(key = "history_title", span = { GridItemSpan(maxLineSpan) }) {
                Text("Call history", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            if (vm.calls.isEmpty()) {
                item(key = "history_empty", span = { GridItemSpan(maxLineSpan) }) { Text("No calls yet. Tap a staff button to create one.") }
            }
            items(vm.calls, key = { "c_" + it.callId }, span = { GridItemSpan(maxLineSpan) }) { HistoryRow(it) }
        }
    }

    confirmStaff?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmStaff = null },
            title = { Text("Call ${s.name}?") },
            text = { Text("This creates a SIMULATED call request (local only).") },
            confirmButton = { TextButton(onClick = { vm.createCall(s); confirmStaff = null }) { Text("Call") } },
            dismissButton = { TextButton(onClick = { confirmStaff = null }) { Text("Cancel") } }
        )
    }

    val selected = vm.callById(selectedCallId)
    if (selected != null) CallDialog(selected, vm) { selectedCallId = null }
}

@Composable
fun StaffButton(s: Staff, active: CallRecord?, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(16.dp))
            .background(Palette.color(s.colorIndex)).clickable(onClick = onClick).padding(12.dp)
    ) {
        Text(s.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (active == null) {
            Text(
                "Available", color = Color(0xFF1B5E20), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.dp, vertical = 3.dp)
            )
        } else StatusChip(active.status)
    }
}

@Composable
fun HistoryRow(c: CallRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${c.staffName} (${c.staffId})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
fun CallDialog(call: CallRecord, vm: AppViewModel, onDismiss: () -> Unit) {
    val next = call.status.allowedNext()
    val staffActions = next.filter { it == CallStatus.RECEIVED || it == CallStatus.ON_THE_WAY || it == CallStatus.COMPLETED }
    val adminActions = next.filter { it == CallStatus.CANCELLED || it == CallStatus.MISSED || it == CallStatus.NO_RESPONSE }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${call.staffName} (${call.staffId})") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(call.status)
                Text("Created: ${fmtTime(call.createdAt)}")
                if (staffActions.isNotEmpty()) {
                    Text("Staff response (simulated on this phone):", fontWeight = FontWeight.Bold)
                    staffActions.forEach {
                        Button(onClick = { vm.updateStatus(call.callId, it) }, modifier = Modifier.fillMaxWidth()) { Text(it.label) }
                    }
                }
                if (adminActions.isNotEmpty()) {
                    Text("Administrator actions:", fontWeight = FontWeight.Bold)
                    adminActions.forEach {
                        OutlinedButton(onClick = { vm.updateStatus(call.callId, it) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (it == CallStatus.CANCELLED) "Cancel call" else "Mark ${it.label}")
                        }
                    }
                }
                if (next.isEmpty()) Text("This call is finished.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(vm: AppViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Staff?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Staff?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage staff") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { adding = true }) { Text("+ Add") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("${vm.staff.size} staff members (supports 10-30)", fontSize = 13.sp)
            vm.staff.forEach { s ->
                val active = vm.activeCall(s.id)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Palette.color(s.colorIndex)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            Text("ID ${s.id}  |  ${active?.status?.label ?: "Available"}", fontSize = 12.sp)
                        }
                        TextButton(onClick = { editing = s }) { Text("Edit") }
                        TextButton(onClick = { removing = s }) { Text("Remove") }
                    }
                }
            }
        }
    }
    if (adding) StaffDialog(null, vm) { adding = false }
    editing?.let { StaffDialog(it, vm) { editing = null } }
    removing?.let { s ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${s.name}?") },
            text = { Text("Call history keeps this person's past calls.") },
            confirmButton = {
                TextButton(onClick = { vm.removeStaff(s.id)?.let { vm.message = it }; removing = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun StaffDialog(initial: Staff?, vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var id by remember { mutableStateOf(initial?.id ?: "") }
    var color by remember { mutableStateOf(initial?.colorIndex ?: 0) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add staff" else "Edit staff") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = id, onValueChange = { id = it }, label = { Text("Staff ID (unique)") },
                    singleLine = true, enabled = initial == null
                )
                Text("Button colour: ${Palette.names[color]}")
                Palette.colors.chunked(5).forEachIndexed { row, list ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEachIndexed { j, c ->
                            val idx = row * 5 + j
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(c)
                                    .border(if (idx == color) 4.dp else 0.dp, Color.Black, CircleShape)
                                    .clickable { color = idx }
                            )
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = if (initial == null) vm.addStaff(name, id, color) else vm.editStaff(initial.id, name, color)
                if (err == null) onDismiss() else error = err
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
