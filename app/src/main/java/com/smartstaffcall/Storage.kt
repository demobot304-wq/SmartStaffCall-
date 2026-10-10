package com.smartstaffcall

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** PROTOTYPE local storage (SharedPreferences + JSON). Production version uses Firestore. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("smart_staff_call", Context.MODE_PRIVATE)

    fun loadStaff(): List<Staff>? {
        val text = prefs.getString("staff", null) ?: return null
        return try {
            val a = JSONArray(text)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Staff(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    colorIndex = o.getInt("color"),
                    // Older saved data has no post: those people become Peon, not pinned.
                    post = Post.values().firstOrNull { p -> p.name == o.optString("post") } ?: Post.PEON,
                    pinned = o.optBoolean("pinned", false),
                    responsibilities = o.optString("resp", ""),
                    currentWork = o.optString("work", ""),
                    presence = Presence.values().firstOrNull { p -> p.name == o.optString("presence") } ?: Presence.PRESENT,
                    presenceReason = o.optString("reason", ""),
                    awayUntil = if (o.has("awayUntil")) o.getLong("awayUntil") else null
                )
            }
        } catch (e: Exception) { null }
    }

    fun saveStaff(list: List<Staff>) {
        val a = JSONArray()
        list.forEach { s ->
            val o = JSONObject()
                .put("id", s.id).put("name", s.name).put("color", s.colorIndex)
                .put("post", s.post.name).put("pinned", s.pinned)
                .put("resp", s.responsibilities).put("work", s.currentWork)
                .put("presence", s.presence.name).put("reason", s.presenceReason)
            s.awayUntil?.let { o.put("awayUntil", it) }
            a.put(o)
        }
        prefs.edit().putString("staff", a.toString()).apply()
    }

    fun loadCalls(): List<CallRecord> {
        val text = prefs.getString("calls", null) ?: return emptyList()
        return try {
            val a = JSONArray(text)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                CallRecord(
                    callId = o.getString("callId"),
                    staffId = o.getString("staffId"),
                    staffName = o.getString("staffName"),
                    createdAt = o.getLong("createdAt"),
                    status = CallStatus.valueOf(o.getString("status")),
                    receivedAt = if (o.has("receivedAt")) o.getLong("receivedAt") else null,
                    onTheWayAt = if (o.has("onTheWayAt")) o.getLong("onTheWayAt") else null,
                    completedAt = if (o.has("completedAt")) o.getLong("completedAt") else null,
                    endedAt = if (o.has("endedAt")) o.getLong("endedAt") else null,
                    staffPost = o.optString("staffPost", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveCalls(list: List<CallRecord>) {
        val a = JSONArray()
        list.forEach { c ->
            val o = JSONObject()
                .put("callId", c.callId).put("staffId", c.staffId).put("staffName", c.staffName)
                .put("createdAt", c.createdAt).put("status", c.status.name).put("staffPost", c.staffPost)
            c.receivedAt?.let { o.put("receivedAt", it) }
            c.onTheWayAt?.let { o.put("onTheWayAt", it) }
            c.completedAt?.let { o.put("completedAt", it) }
            c.endedAt?.let { o.put("endedAt", it) }
            a.put(o)
        }
        prefs.edit().putString("calls", a.toString()).apply()
    }

    fun loadPresenceLog(): List<PresenceRecord> {
        val text = prefs.getString("presence_log", null) ?: return emptyList()
        return try {
            val a = JSONArray(text)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                PresenceRecord(
                    id = o.getString("id"),
                    staffId = o.getString("staffId"),
                    staffName = o.getString("staffName"),
                    type = o.getString("type"),
                    reason = o.optString("reason", ""),
                    startedAt = o.getLong("startedAt"),
                    endedAt = if (o.has("endedAt")) o.getLong("endedAt") else null
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun savePresenceLog(list: List<PresenceRecord>) {
        val a = JSONArray()
        list.forEach { p ->
            val o = JSONObject()
                .put("id", p.id).put("staffId", p.staffId).put("staffName", p.staffName)
                .put("type", p.type).put("reason", p.reason).put("startedAt", p.startedAt)
            p.endedAt?.let { o.put("endedAt", it) }
            a.put(o)
        }
        prefs.edit().putString("presence_log", a.toString()).apply()
    }
}
