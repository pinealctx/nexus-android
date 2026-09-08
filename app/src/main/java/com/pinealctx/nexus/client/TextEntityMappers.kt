package com.pinealctx.nexus.client

import com.pinealctx.nexus.core.TextEntityData
import com.shared.v1.HashtagEntity
import com.shared.v1.MentionEntity
import com.shared.v1.MessageEntity
import com.shared.v1.MessageEntityType
import com.shared.v1.PhoneEntity
import com.shared.v1.UrlEntity

internal fun MessageEntity.toTextEntityData() = TextEntityData(
    type = type.takeUnless { it == MessageEntityType.UNRECOGNIZED } ?: MessageEntityType.MESSAGE_ENTITY_TYPE_UNSPECIFIED,
    offset = offset, length = length,
    userId = mention.userId, isAll = mention.isAll,
    value = when (dataCase) {
        MessageEntity.DataCase.URL -> url.url
        MessageEntity.DataCase.PHONE -> phone.phoneNumber
        MessageEntity.DataCase.HASHTAG -> hashtag.tag
        else -> ""
    }
)

internal fun TextEntityData.toProto(): MessageEntity = MessageEntity.newBuilder()
    .setType(type).setOffset(offset).setLength(length)
    .apply {
        when (type) {
            MessageEntityType.MESSAGE_ENTITY_TYPE_MENTION ->
                setMention(MentionEntity.newBuilder().setUserId(userId).setIsAll(isAll))
            MessageEntityType.MESSAGE_ENTITY_TYPE_URL -> setUrl(UrlEntity.newBuilder().setUrl(value))
            MessageEntityType.MESSAGE_ENTITY_TYPE_PHONE -> setPhone(PhoneEntity.newBuilder().setPhoneNumber(value))
            MessageEntityType.MESSAGE_ENTITY_TYPE_HASHTAG -> setHashtag(HashtagEntity.newBuilder().setTag(value))
            else -> Unit
        }
    }.build()
