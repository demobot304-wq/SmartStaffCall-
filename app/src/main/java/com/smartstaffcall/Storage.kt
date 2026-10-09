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
                Staff(o.getString("id"), o.getString("name"), o.getInt("color"))
            }
        } catch (e: Exception) { null }
    }

    fun saveStaff(list: List<Staff>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.colorIndex)) }
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
                    endedAt = if (o.has("endedAt")) o.getLong("endedAt") else null
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveCalls(list: List<CallRecord>) {
        val a = JSONArray()
        list.forEach { c ->
            val o = JSONObject()
                .put("callId", c.callId).put("staffId", c.staffId).put("staffName", c.staffName)
                .put("createdAt", c.createdAt).put("status", c.status.name)
            c.receivedAt?.let { o.put("receivedAt", it) }
            c.onTheWayAt?.let { o.put("onTheWayAt", it) }
            c.completedAt?.let { o.put("completedAt", it) }
            c.endedAt?.let { o.put("endedAt", it) }
            a.put(o)
        }
        prefs.edit().putString("calls", a.toString()).apply()
    }
}
