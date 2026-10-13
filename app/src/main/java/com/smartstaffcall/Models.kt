package com.smartstaffcall

import androidx.compose.ui.graphics.Color

/** All call statuses. Normal flow: PENDING -> RECEIVED -> ON_THE_WAY -> COMPLETED. */
enum class CallStatus(val label: String, private val colorHex: Long) {
    PENDING("Pending", 0xFFB26A00),
    RECEIVED("Received", 0xFF1565C0),
    ON_THE_WAY("On the way", 0xFF6A1B9A),
    COMPLETED("Completed", 0xFF2E7D32),
    CANCELLED("Cancelled", 0xFF616161),
    MISSED("Missed", 0xFFC62828),
    NO_RESPONSE("No response", 0xFF4E342E);

    val color: Color get() = Color(colorHex)
    val isActive: Boolean get() = this == PENDING || this == RECEIVED || this == ON_THE_WAY

    /** The only statuses this status may move to (transition validation). */
    fun allowedNext(): List<CallStatus> = when (this) {
        PENDING -> listOf(RECEIVED, CANCELLED, MISSED, NO_RESPONSE)
        RECEIVED -> listOf(ON_THE_WAY, CANCELLED)
        ON_THE_WAY -> listOf(COMPLETED, CANCELLED)
        else -> emptyList()
    }
}

/** A post (job title). The admin controls this list and its order (most senior first). */
data class PostDef(val id: String, val name: String)

fun defaultPosts(): List<PostDef> = listOf(
    PostDef("MANAGER", "Manager"),
    PostDef("ASSISTANT_MANAGER", "Assistant Manager"),
    PostDef("ASSISTANT_GRADE_1", "Assistant Grade 1"),
    PostDef("ASSISTANT_GRADE_2", "Assistant Grade 2"),
    PostDef("ASSISTANT_GRADE_3", "Assistant Grade 3"),
    PostDef("DRIVER", "Driver"),
    PostDef("PEON", "Peon")
)

enum class Presence { PRESENT, AWAY, LEAVE }

/** What a dashboard button looks like right now. Worked out from calls and presence. */
enum class Visual(val label: String, val bg: Color, val fg: Color) {
    AVAILABLE("Available", Color(0xFF2E7D32), Color.White),
    CALLING("Calling", Color(0xFF1565C0), Color.White),
    NO_RESPONSE("No response", Color(0xFFEF6C00), Color.White),
    AWAY("Away", Color(0xFFF9A825), Color(0xFF3E2C00)),
    LEAVE("On leave", Color.White, Color(0xFF263238))
}

object Timing {
    const val CALL_WAIT_MS = 10_000L        // blue: how long staff have to respond
    const val ORANGE_MS = 5 * 60_000L       // orange: shown after no response
    const val AWAY_MS = 60 * 60_000L        // yellow: away from office
    const val HOLD_MS = 5000                // hold a button this long to see details
}

data class Staff(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val postId: String = "PEON",
    val pinned: Boolean = false,
    val responsibilities: String = "",
    val currentWork: String = "",
    val presence: Presence = Presence.PRESENT,
    val presenceReason: String = "",
    val awayUntil: Long? = null,
    val profileSet: Boolean = false // true after the staff member did the first-time setup
)

data class CallRecord(
    val callId: String,
    val staffId: String,
    val staffName: String,
    val createdAt: Long,
    val status: CallStatus = CallStatus.PENDING,
    val receivedAt: Long? = null,
    val onTheWayAt: Long? = null,
    val completedAt: Long? = null,
    val endedAt: Long? = null, // time of cancel / missed / no response
    val staffPost: String = ""
) {
    /** Response time = created -> received, if the call was received. */
    val responseMs: Long? get() = receivedAt?.let { it - createdAt }
}

/** A record of a period when someone was Away or On leave. type is "AWAY" or "LEAVE". */
data class PresenceRecord(
    val id: String,
    val staffId: String,
    val staffName: String,
    val type: String,
    val reason: String,
    val startedAt: Long,
    val endedAt: Long? = null
)

enum class RequestStatus(val label: String) {
    PENDING("Waiting"),
    APPROVED("Approved"),
    REJECTED("Rejected")
}

/** A staff member's request to change their post / responsibilities / current work. The head must approve. */
data class ChangeRequest(
    val id: String,
    val staffId: String,
    val staffName: String,
    val oldPostId: String,
    val oldResp: String,
    val oldWork: String,
    val newPostId: String,
    val newResp: String,
    val newWork: String,
    val createdAt: Long,
    val status: RequestStatus = RequestStatus.PENDING,
    val decidedAt: Long? = null
)

object Palette {
    val names = listOf("Blue", "Green", "Purple", "Teal", "Orange", "Red", "Brown", "Pink", "Indigo", "Olive")
    val colors = listOf(
        Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A), Color(0xFF00695C), Color(0xFFE65100),
        Color(0xFFC62828), Color(0xFF4E342E), Color(0xFFAD1457), Color(0xFF283593), Color(0xFF558B2F)
    )
    fun color(i: Int): Color = colors[i.coerceIn(0, colors.lastIndex)]
}