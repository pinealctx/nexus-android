package com.pinealctx.nexus.client

import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.GroupEventType
import com.pinealctx.nexus.core.MessageStreamPhase
import com.shared.v1.GroupContent
import com.shared.v1.MemberInfo
import com.shared.v1.MemberJoinedEvent
import com.shared.v1.MessageBody
import com.shared.v1.MessageType
import com.shared.v1.StreamContent
import com.shared.v1.StreamPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMappersTest {
    @Test
    fun groupMessageWithoutOneofContentStillMapsToGroupEvent() {
        val body = MessageBody.newBuilder()
            .setType(MessageType.MESSAGE_TYPE_GROUP)
            .build()

        assertEquals(
            MessageContent.GroupEvent(0, GroupEventType.UNKNOWN),
            body.toMessageContent()
        )
    }

    @Test
    fun groupMessagePreservesStructuredEventSemantics() {
        val body = MessageBody.newBuilder()
            .setType(MessageType.MESSAGE_TYPE_GROUP)
            .setGroup(
                GroupContent.newBuilder()
                    .setGroupId(81)
                    .setMemberJoined(
                        MemberJoinedEvent.newBuilder()
                            .addMembers(MemberInfo.newBuilder().setUserId(11))
                            .addMembers(MemberInfo.newBuilder().setUserId(12))
                            .setInviter(MemberInfo.newBuilder().setUserId(7))
                    )
            )
            .build()

        assertEquals(
            MessageContent.GroupEvent(
                groupId = 81,
                type = GroupEventType.MEMBER_JOINED,
                memberIds = listOf(11, 12),
                inviterId = 7
            ),
            body.toMessageContent()
        )
    }

    @Test
    fun streamMessagePreservesLifecycleAndPayload() {
        val body = MessageBody.newBuilder()
            .setType(MessageType.MESSAGE_TYPE_STREAM)
            .setStream(
                StreamContent.newBuilder()
                    .setPhase(StreamPhase.STREAM_PHASE_DELTA)
                    .setSeq(3)
                    .setDelta("next")
                    .setContentType("text/markdown")
                    .setAccumulatedText("previous")
            )
            .build()

        assertEquals(
            MessageContent.Stream(
                phase = MessageStreamPhase.DELTA,
                sequence = 3,
                delta = "next",
                contentType = "text/markdown",
                accumulatedText = "previous"
            ),
            body.toMessageContent()
        )
    }
}
