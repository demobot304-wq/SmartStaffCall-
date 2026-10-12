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
    val presenceLog = mutableStateListOf<PresenceRecord>() // newest first
    var message by mutableStateOf<String?>(null)

    init {
        val saved = storage.loadStaff()
        if (saved == null) {
            staff.addAll(sampleStaff())
            storage.saveStaff(staff.toList())
        } else staff.addAll(saved)
        calls.addAll(storage.loadCalls().sortedByDescending { it.createdAt })
        presenceLog.addAll(storage.loadPresenceLog().sortedByDescending { it.startedAt })
    }

    private fun sampleStaff() = listOf(
        Staff("S01", "Alice Johnson", 0, Post.MANAGER),
        Staff("S02", "Bob Smith", 1, Post.ASSISTANT_MANAGER),
        Staff("S03", "Carla Gomez", 2, Post.ASSISTANT_GRADE_1),
        Staff("S04", "David Lee", 4, Post.DRIVER),
        Staff("S05", "Emma Brown", 5, Post.PEON, pinned = true),
        Staff("S06", "Farid Khan", 7, Post.ASSISTANT_GRADE_3)
    )

    fun activeCall(staffId: String): CallRecord? =
        calls.firstOrNull { it.staffId == staffId && it.status.isActive }

    fun callById(id: String?): CallRecord? = calls.firstOrNull { it.callId == id }

    /** When the orange "no response" period ends, or null if the last call was not a no-response. */
    private fun orangeUntil(staffId: String): Long? {
        val last = calls.firstOrNull { it.staffId == staffId } ?: return null
        val ended = last.endedAt ?: return null
        return if (last.status == CallStatus.NO_RESPONSE) ended + Timing.ORANGE_MS else null
    }

    /** Colour rule: leave (white) > away (yellow) > calling (blue) > no response (orange) > available (green). */
    fun visualOf(s: Staff, now: Long): Visual {
        if (s.presence == Presence.LEAVE) return Visual.LEAVE
        if (s.presence == Presence.AWAY) return Visual.AWAY
        if (activeCall(s.id)?.status == CallStatus.PENDING) return Visual.CALLING
        val endAt = orangeUntil(s.id)
        if (endAt != null && endAt > now) return Visual.NO_RESPONSE
        return Visual.AVAILABLE
    }

    fun statusLine(s: Staff, v: Visual, now: Long): String = when (v) {
        Visual.AVAILABLE -> activeCall(s.id)?.status?.label ?: "Available"
        Visual.CALLING -> {
            val created = activeCall(s.id)?.createdAt ?: now
            val left = ((Timing.CALL_WAIT_MS - (now - created) + 999) / 1000).coerceIn(0L, 10L)
            "Calling... ${left}s"
        }
        Visual.NO_RESPONSE -> "No response  " + fmtClock((orangeUntil(s.id) ?: now) - now)
        Visual.AWAY -> "Away  " + fmtClock((s.awayUntil ?: now) - now)
        Visual.LEAVE -> "On leave"
    }

    /** Called several times a second by the screen: ends timed-out calls and finished "away" periods. */
    fun tick(now: Long) {
        var callsChanged = false
        for (i in calls.indices) {
            val c = calls[i]
            if (c.status == CallStatus.PENDING && now - c.createdAt >= Timing.CALL_WAIT_MS) {
                calls[i] = c.copy(status = CallStatus.NO_RESPONSE, endedAt = c.createdAt + Timing.CALL_WAIT_MS)
                Notifier.clear(ctx, c.callId)
                callsChanged = true
            }
        }
        if (callsChanged) storage.saveCalls(calls.toList())

        var staffChanged = false
        for (i in staff.indices) {
            val s = staff[i]
            val endAt = s.awayUntil
            if (s.presence == Presence.AWAY && endAt != null && endAt <= now) {
                staff[i] = s.copy(presence = Presence.PRESENT, presenceReason = "", awayUntil = null)
                closeOpenLog(s.id, endAt)
                staffChanged = true
            }
        }
        if (staffChanged) storage.saveStaff(staff.toList())
    }

    /** Creates a SIMULATED call. Returns the new call id, or null if not created. */
    fun createCall(s: Staff): String? {
        if (staff.none { it.id == s.id }) { message = "Staff member no longer exists."; return null }
        if (activeCall(s.id) != null) { message = "${s.name} already has an active call."; return null }
        val call = CallRecord(
            callId = UUID.randomUUID().toString(),
            staffId = s.id,
            staffName = s.name,
            createdAt = System.currentTimeMillis(),
            staffPost = s.post.label
        )
        calls.add(0, call)
        storage.saveCalls(calls.toList())
        Notifier.show(ctx, call.callId, s.name)
        return call.callId
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

    private fun closeOpenLog(staffId: String, endTime: Long) {
        val j = presenceLog.indexOfFirst { it.staffId == staffId && it.endedAt == null }
        if (j >= 0) {
            presenceLog[j] = presenceLog[j].copy(endedAt = endTime)
            storage.savePresenceLog(presenceLog.toList())
        }
    }

    /** Away lasts 1 hour then ends by itself. Leave stays until the admin makes the person available. */
    fun setPresence(staffId: String, p: Presence, reason: String): String? {
        val i = staff.indexOfFirst { it.id == staffId }
        if (i < 0) return "Staff member not found."
        if (p != Presence.PRESENT && reason.isBlank()) return "Please enter a reason."
        if (reason.trim().length > 100) return "Reason must be 100 characters or fewer."
        val s = staff[i]
        val now = System.currentTimeMillis()
        closeOpenLog(s.id, now)
        if (p == Presence.PRESENT) {
            staff[i] = s.copy(presence = Presence.PRESENT, presenceReason = "", awayUntil = null)
        } else {
            staff[i] = s.copy(
                presence = p,
                presenceReason = reason.trim(),
                awayUntil = if (p == Presence.AWAY) now + Timing.AWAY_MS else null
            )
            presenceLog.add(0, PresenceRecord(UUID.randomUUID().toString(), s.id, s.name, p.name, reason.trim(), now))
            storage.savePresenceLog(presenceLog.toList())
        }
        storage.saveStaff(staff.toList())
        return null
    }

    private fun validate(name: String, id: String, resp: String, work: String): String? {
        if (name.isBlank()) return "Name cannot be empty."
        if (name.trim().length > 30) return "Name must be 30 characters or fewer."
        if (id.isBlank()) return "Staff ID cannot be empty."
        if (!id.trim().all { it.isLetterOrDigit() || it == '-' || it == '_' }) return "ID may only contain letters, digits, - and _."
        if (resp.length > 300 || work.length > 300) return "Responsibilities and current work must be 300 characters or fewer."
        return null
    }

    /** Returns an error message, or null on success. */
    fun addStaff(name: String, id: String, color: Int, post: Post, pinned: Boolean, resp: String, work: String): String? {
        validate(name, id, resp, work)?.let { return it }
        if (staff.any { it.id.equals(id.trim(), ignoreCase = true) }) return "That staff ID is already used."
        staff.add(Staff(id.trim(), name.trim(), color, post, pinned, resp.trim(), work.trim()))
        storage.saveStaff(staff.toList())
        return null
    }

    /** The ID cannot be changed after creation (history refers to it). */
    fun editStaff(id: String, name: String, color: Int, post: Post, pinned: Boolean, resp: String, work: String): String? {
        validate(name, id, resp, work)?.let { return it }
        val i = staff.indexOfFirst { it.id == id }
        if (i < 0) return "Staff member not found."
        staff[i] = staff[i].copy(name = name.trim(), colorIndex = color, post = post, pinned = pinned,
            responsibilities = resp.trim(), currentWork = work.trim())
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