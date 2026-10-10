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

val AppBg = Color(0xFFF4F6FB)
val SoftGray = Color(0xFF6B7280)

fun fmtTime(t: Long?): String =
    if (t == null) "-" else SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(t))

fun fmtDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}

/** Countdown text such as 4:32 or 1:02:10. */
fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
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

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SoftGray, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun AppRoot(vm: AppViewModel, openCallId: String?, onOpenHandled: () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Color(0xFF3D5AFE), background = AppBg, surface = Color.White)
    ) {
        AppContent(vm, openCallId, onOpenHandled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(vm: AppViewModel, openCallId: String?, onOpenHandled: () -> Unit) {
    var showManage by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var callSheetId by remember { mutableStateOf<String?>(null) }
    var actionStaffId by remember { mutableStateOf<String?>(null) }
    var detailsStaffId by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val snackbar = remember { SnackbarHostState() }

    // Clock: runs the 10 s, 5 min and 1 hour timers.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            vm.tick(now)
            delay(250)
        }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    LaunchedEffect(openCallId) {
        if (openCallId != null) {
            if (vm.callById(openCallId) != null) callSheetId = openCallId
            onOpenHandled()
        }
    }

    if (showManage) {
        BackHandler { showManage = false }
        ManageScreen(vm, snackbar) { showManage = false }
        return
    }

    // Tap: open the active call if there is one, otherwise open the call / status sheet.
    val onStaffTap: (Staff) -> Unit = { st ->
        val active = vm.activeCall(st.id)
        if (active != null) callSheetId = active.callId else actionStaffId = st.id
    }

    Scaffold(
        containerColor = AppBg,
        topBar = {
            TopAppBar(
                title = { Text("Smart Staff Call", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBg),
                actions = { TextButton(onClick = { showManage = true }) { Text("Manage staff") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        val q = query.trim()
        val shown = vm.staff.filter { it.name.contains(q, ignoreCase = true) }
        val pinned = shown.filter { it.pinned }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad).fillMaxSize()
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "PROTOTYPE: calls and notifications are simulated on this phone only. Nothing is sent to any other device.",
                        fontSize = 12.sp,
                        color = Color(0xFF7A4B00),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF3D6)).padding(10.dp)
                    )
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text("Search staff by name") }, singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (shown.isEmpty()) {
                item(key = "nomatch", span = { GridItemSpan(maxLineSpan) }) { Text("No staff match your search.") }
            }
            if (pinned.isNotEmpty()) {
                item(key = "h_quick", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Quick call") }
                items(pinned, key = { "q_" + it.id }) { s ->
                    StaffCell(vm, s, now, onTap = { onStaffTap(s) }, onHold = { detailsStaffId = s.id })
                }
            }
            Post.values().forEach { post ->
                val group = shown.filter { !it.pinned && it.post == post }
                if (group.isNotEmpty()) {
                    item(key = "h_" + post.name, span = { GridItemSpan(maxLineSpan) }) { SectionTitle(post.label) }
                    items(group, key = { "s_" + it.id }) { s ->
                        StaffCell(vm, s, now, onTap = { onStaffTap(s) }, onHold = { detailsStaffId = s.id })
                    }
                }
            }
            item(key = "history_title", span = { GridItemSpan(maxLineSpan) }) {
                Text("Call history", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            }
            if (vm.calls.isEmpty()) {
                item(key = "history_empty", span = { GridItemSpan(maxLineSpan) }) { Text("No calls yet. Tap a staff button to create one.") }
            }
            items(vm.calls, key = { "c_" + it.callId }, span = { GridItemSpan(maxLineSpan) }) { HistoryRow(it) }
            if (vm.presenceLog.isNotEmpty()) {
                item(key = "presence_title", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Away and leave log", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
                items(vm.presenceLog.take(30), key = { "p_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { PresenceRow(it) }
            }
        }
    }

    val actionStaff = vm.staff.firstOrNull { it.id == actionStaffId }
    if (actionStaff != null) {
        ActionSheet(vm, actionStaff, onClose = { actionStaffId = null }, onCalled = { id -> actionStaffId = null; callSheetId = id })
    }
    val detailsStaff = vm.staff.firstOrNull { it.id == detailsStaffId }
    if (detailsStaff != null) DetailsSheet(detailsStaff) { detailsStaffId = null }
    val sheetCall = vm.callById(callSheetId)
    if (sheetCall != null) CallSheet(sheetCall, vm) { callSheetId = null }
}

@Composable
fun StaffCell(vm: AppViewModel, s: Staff, now: Long, onTap: () -> Unit, onHold: () -> Unit) {
    val visual = vm.visualOf(s, now)
    val call = vm.activeCall(s.id)
    val progress: Float? =
        if (visual == Visual.CALLING && call != null)
            1f - ((now - call.createdAt).toFloat() / Timing.CALL_WAIT_MS).coerceIn(0f, 1f)
        else null
    StaffButton(s, visual, vm.statusLine(s, visual, now), progress, onTap, onHold)
}

@Composable
fun StaffButton(s: Staff, visual: Visual, line: String, progress: Float?, onTap: () -> Unit, onHold: () -> Unit) {
    val bg by animateColorAsState(visual.bg, tween(400), label = "bg")
    val fg by animateColorAsState(visual.fg, tween(400), label = "fg")
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "scale")
    var hold by remember { mutableFloatStateOf(0f) }
    var holdFired by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tapNow by rememberUpdatedState(onTap)
    val holdNow by rememberUpdatedState(onHold)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, Color(0x22000000), RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        holdFired = false
                        hold = 0f
                        // Holding for 5 seconds shows the details card.
                        val job = scope.launch {
                            animate(0f, 1f, animationSpec = tween(Timing.HOLD_MS, easing = LinearEasing)) { v, _ -> hold = v }
                            holdFired = true
                            holdNow()
                        }
                        tryAwaitRelease()
                        job.cancel()
                        pressed = false
                        hold = 0f
                    },
                    onTap = { if (!holdFired) tapNow() }
                )
            }
    ) {
        // Thin stripe in the staff member's own colour.
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(8.dp).background(Palette.color(s.colorIndex)))
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(s.name, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.post.label, color = fg.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(line, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = fg,
                        trackColor = fg.copy(alpha = 0.25f)
                    )
                }
            }
        }
        if (hold > 0f) {
            CircularProgressIndicator(
                progress = { hold },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp),
                color = fg,
                strokeWidth = 3.dp
            )
        }
    }
}

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
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTh
