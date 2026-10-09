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

data class Staff(val id: String, val name: String, val colorIndex: Int)

data class CallRecord(
    val callId: String,
    val staffId: String,
    val staffName: String,
    val createdAt: Long,
    val status: CallStatus = CallStatus.PENDING,
    val receivedAt: Long? = null,
    val onTheWayAt: Long? = null,
    val completedAt: Long? = null,
    val endedAt: Long? = null // time of cancel / missed / no response
) {
    /** Response time = created -> received, if the call was received. */
    val responseMs: Long? get() = receivedAt?.let { it - createdAt }
}

object Palette {
    val names = listOf("Blue", "Green", "Purple", "Teal", "Orange", "Red", "Brown", "Pink", "Indigo", "Olive")
    val colors = listOf(
        Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A), Color(0xFF00695C), Color(0xFFE65100),
        Color(0xFFC62828), Color(0xFF4E342E), Color(0xFFAD1457), Color(0xFF283593), Color(0xFF558B2F)
    )
    fun color(i: Int): Color = colors[i.coerceIn(0, colors.lastIndex)]
}
