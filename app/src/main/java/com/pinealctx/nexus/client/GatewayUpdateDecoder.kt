package com.pinealctx.nexus.client

import com.api.v1.Update
import com.google.protobuf.InvalidProtocolBufferException
import com.shared.v1.NonSnUpdate
import com.shared.v1.SnUpdate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewayUpdateDecoder @Inject constructor() {
    @Throws(InvalidProtocolBufferException::class)
    fun decode(bytes: ByteArray): GatewayUpdate {
        return classify(Update.parseFrom(bytes))
    }

    fun classify(update: Update): GatewayUpdate {
        return when (update.updateCase) {
            Update.UpdateCase.SN_UPDATE -> GatewayUpdate.Sequenced(update, update.snUpdate)
            Update.UpdateCase.NON_SN_UPDATE -> GatewayUpdate.Ephemeral(update, update.nonSnUpdate)
            Update.UpdateCase.UPDATE_NOT_SET -> GatewayUpdate.Unknown(update)
        }
    }
}

sealed interface GatewayUpdate {
    val raw: Update

    data class Sequenced(
        override val raw: Update,
        val payload: SnUpdate
    ) : GatewayUpdate {
        val sn: Int = payload.sn
        val kind: SnUpdate.UpdateCase = payload.updateCase
    }

    data class Ephemeral(
        override val raw: Update,
        val payload: NonSnUpdate
    ) : GatewayUpdate {
        val kind: NonSnUpdate.UpdateCase = payload.updateCase
    }

    data class Unknown(
        override val raw: Update
    ) : GatewayUpdate
}
