package com.pinealctx.nexus.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationLedger @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("notification_ledger", Context.MODE_PRIVATE)

    @Synchronized
    fun contains(owner: Int, key: String, now: Long = System.currentTimeMillis()): Boolean {
        val seen = JSONObject(prefs.getString("seen_$owner", "{}")!!)
        return seen.has(key) && now - seen.optLong(key) <= 48 * 60 * 60 * 1000L
    }

    @Synchronized
    fun accept(owner: Int, key: String, now: Long = System.currentTimeMillis()): Boolean {
        val seen = JSONObject(prefs.getString("seen_$owner", "{}")!!)
        seen.keys().asSequence().toList().filter { now - seen.optLong(it) > 48 * 60 * 60 * 1000L }.forEach(seen::remove)
        if (seen.has(key)) return false
        seen.put(key, now)
        if (seen.length() > 5000) seen.keys().asSequence().sortedBy { seen.optLong(it) }.take(seen.length() - 5000).toList().forEach(seen::remove)
        check(prefs.edit().putString("seen_$owner", seen.toString()).commit())
        return true
    }

    @Synchronized
    fun messages(owner: Int, conversationId: String): List<Map<String, String>> {
        val rows = JSONArray(prefs.getString("messages_${owner}_$conversationId", "[]")!!)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            row.keys().asSequence().associateWith { row.getString(it) }
        }
    }

    @Synchronized
    fun save(owner: Int, conversationId: String, rows: List<Map<String, String>>) {
        val edit = prefs.edit()
        val key = "messages_${owner}_$conversationId"
        if (rows.isEmpty()) edit.remove(key)
        else edit.putString(key, JSONArray(rows.takeLast(8).map { JSONObject(it) }).toString())
        check(edit.commit())
    }

    fun readWaterline(owner: Int, conversationId: String): Long = prefs.getLong("read_${owner}_$conversationId", 0)

    @Synchronized
    fun markRead(owner: Int, conversationId: String, messageId: Long) {
        check(prefs.edit().putLong("read_${owner}_$conversationId", maxOf(messageId, readWaterline(owner, conversationId))).commit())
    }

    @Synchronized
    fun clear() { check(prefs.edit().clear().commit()) }
}
