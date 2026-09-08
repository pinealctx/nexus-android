package com.pinealctx.nexus.local

import com.pinealctx.nexus.core.TextEntityData
import com.shared.v1.MessageEntityType
import org.json.JSONArray
import org.json.JSONObject

internal fun List<TextEntityData>.encodeTextEntities(): String = JSONArray(map { entity ->
    JSONObject().apply {
        put("type", if (entity.type == MessageEntityType.UNRECOGNIZED) 0 else entity.type.number)
        put("offset", entity.offset)
        put("length", entity.length)
        put("user_id", entity.userId)
        put("is_all", entity.isAll)
        put("value", entity.value)
    }
}).toString()

internal fun String?.decodeTextEntities(): List<TextEntityData> = runCatching {
    val array = JSONArray(this ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        TextEntityData(
            type = MessageEntityType.forNumber(item.optInt("type")) ?: MessageEntityType.MESSAGE_ENTITY_TYPE_UNSPECIFIED,
            offset = item.optInt("offset", -1), length = item.optInt("length"),
            userId = item.optInt("user_id"), isAll = item.optBoolean("is_all"), value = item.optString("value")
        )
    }
}.getOrDefault(emptyList())
