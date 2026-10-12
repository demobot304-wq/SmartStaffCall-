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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(vm: AppViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Staff?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Staff?>(null) }
    Scaffold(
        containerColor = AppBg,
        topBar = {
            TopAppBar(
                title = { Text("Manage staff", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBg),
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { adding = true }) { Text("+ Add") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("${vm.staff.size} staff members (supports 10-30)", fontSize = 13.sp, color = SoftGray)
            vm.staff.forEach { s ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Palette.color(s.colorIndex)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            Text(
                                s.post.label + "  |  ID " + s.id + if (s.pinned) "  |  Quick call" else "",
                                fontSize = 12.sp, color = SoftGray
                            )
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
    var post by remember { mutableStateOf(initial?.post ?: Post.PEON) }
    var pinned by remember { mutableStateOf(initial?.pinned ?: true) }
    var resp by remember { mutableStateOf(initial?.responsibilities ?: "") }
    var work by remember { mutableStateOf(initial?.currentWork ?: "") }
    var menuOpen by remember { mutableStateOf(false) }
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
                Text("Post")
                Box {
                    OutlinedButton(onClick = { menuOpen = true }) { Text(post.label) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Post.values().forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.label) },
                                onClick = {
                                    post = p
                                    menuOpen = false
                                    if (initial == null) pinned = (p == Post.PEON)
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show in Quick call row", modifier = Modifier.weight(1f))
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
                OutlinedTextField(
                    value = resp, onValueChange = { resp = it }, label = { Text("Responsibilities") },
                    singleLine = false, minLines = 2
                )
                OutlinedTextField(
                    value = work, onValueChange = { work = it }, label = { Text("Current work") },
                    singleLine = false, minLines = 2
                )
                Text("Stripe colour: ${Palette.names[color]}")
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
                val err = if (initial == null) vm.addStaff(name, id, color, post, pinned, resp, work)
                else vm.editStaff(initial.id, name, color, post, pinned, resp, work)
                if (err == null) onDismiss() else error = err
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// END OF FILE