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
        val until = orangeUntil(s.id)
        if (until != null && now
