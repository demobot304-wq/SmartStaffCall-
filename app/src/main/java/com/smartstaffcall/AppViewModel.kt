package com.smartstaffcall

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)
    private val ctx = app.applicationContext

    val staff = mutableStateListOf<Staff>()
    val calls = mutableStateListOf<CallRecord>() // newest first
    var message by mutableStateOf<String?>(null)

    init {
        val saved = storage.loadStaff()
        if (saved == null) {
            staff.addAll(sampleStaff())
            storage.saveStaff(staff.toList())
        } else staff.addAll(saved)
        calls.addAll(storage.loadCalls().sortedByDescending { it.createdAt })
    }

    private fun sampleStaff() = listOf(
        Staff("S01", "Alice Johnson", 0), Staff("S02", "Bob Smith", 1), Staff("S03", "Carla Gomez", 2),
        Staff("S04", "David Lee", 4), Staff("S05", "Emma Brown", 5), Staff("S06", "Farid Khan", 7)
    )

    fun activeCall(staffId: String): CallRecord? =
        calls.firstOrNull { it.staffId == staffId && it.status.isActive }

    fun callById(id: String?): CallRecord? = calls.firstOrNull { it.callId == id }

    /** Creates a SIMULATED call. Blocks duplicates while the staff member has an active call. */
    fun createCall(s: Staff) {
        if (staff.none { it.id == s.id }) { message = "Staff member no longer exists."; return }
        if (activeCall(s.id) != null) { message = "${s.name} already has an active call."; return }
        val call = CallRecord(UUID.randomUUID().toString(), s.id, s.name, System.currentTimeMillis())
        calls.add(0, call)
        storage.saveCalls(calls.toList())
        Notifier.show(ctx, call.callId, s.name)
        message = "Simulated call created for ${s.name}."
    }

    fun updateStatus(callId: String, next: CallStatus) {
        val i = calls.indexOfFirst { it.callId == callId }
        if (i < 0) { message = "Call not found."; return }
        val c = calls[i]
        if (next !in c.status.allowedNext()) {
            message = "Not allowed: ${c.status.label} to ${next.label}."
            return
        }
        val now = System.currentTimeMillis()
        val updated = when (next) {
            CallStatus.RECEIVED -> c.copy(status = next, receivedAt = now)
            CallStatus.ON_THE_WAY -> c.copy(status = next, onTheWayAt = now)
            CallStatus.COMPLETED -> c.copy(status = next, completedAt = now)
            else -> c.copy(status = next, endedAt = now)
        }
        calls[i] = updated
        storage.saveCalls(calls.toList())
        if (!next.isActive) Notifier.clear(ctx, callId)
    }

    private fun validate(name: String, id: String): String? {
        if (name.isBlank()) return "Name cannot be empty."
        if (name.trim().length > 30) return "Name must be 30 characters or fewer."
        if (id.isBlank()) return "Staff ID cannot be empty."
        if (!id.trim().all { it.isLetterOrDigit() || it == '-' || it == '_' }) return "ID may only contain letters, digits, - and _."
        return null
    }

    /** Returns an error message, or null on success. */
    fun addStaff(name: String, id: String, color: Int): String? {
        validate(name, id)?.let { return it }
        if (staff.any { it.id.equals(id.trim(), ignoreCase = true) }) return "That staff ID is already used."
        staff.add(Staff(id.trim(), name.trim(), color))
        storage.saveStaff(staff.toList())
        return null
    }

    /** The ID cannot be changed after creation (history refers to it). */
    fun editStaff(id: String, name: String, color: Int): String? {
        validate(name, id)?.let { return it }
        val i = staff.indexOfFirst { it.id == id }
        if (i < 0) return "Staff member not found."
        staff[i] = Staff(id, name.trim(), color)
        storage.saveStaff(staff.toList())
        return null
    }

    fun removeStaff(id: String): String? {
        if (activeCall(id) != null) return "Finish or cancel the active call first."
        staff.removeAll { it.id == id }
        storage.saveStaff(staff.toList())
        return null
    }
}
