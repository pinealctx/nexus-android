package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.AgentCommandData
import com.pinealctx.nexus.core.AgentInfoData
import com.pinealctx.nexus.core.MiniAppLaunchResult
import com.pinealctx.nexus.core.NexusClientProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun listFeaturedAgents(limit: Int = 50): List<AgentInfoData> {
        val agents = clientProvider.getOrNull()?.listFeaturedAgents(limit) ?: return emptyList()
        return agents.map { it.toAgentInfoData() }
    }

    fun getAgentInfo(agentUserId: Int): AgentInfoData? {
        return clientProvider.getOrNull()?.getAgentInfo(agentUserId)?.toAgentInfoData()
    }

    fun getMiniAppLaunchData(agentUserId: Int, conversationId: Long, startParam: String = ""): MiniAppLaunchResult {
        val result = clientProvider.get().getMiniAppLaunchData(agentUserId, conversationId, startParam, "android")
        return MiniAppLaunchResult(result.initData, result.miniAppUrl)
    }

    fun createAgent(username: String, nickname: String, description: String): Int =
        clientProvider.get().createAgent(username, nickname, description)

    fun listMyAgents(): List<AgentInfoData> {
        val agents = clientProvider.getOrNull()?.listMyAgents() ?: return emptyList()
        return agents.map { it.toAgentInfoData() }
    }
}

private fun uniffi.nexus_ffi.AgentInfoFfi.toAgentInfoData() = AgentInfoData(
    userId = userId,
    username = username,
    nickname = nickname,
    avatarUrl = avatarUrl,
    signature = signature,
    isSystemAgent = isSystemAgent,
    miniAppEnabled = miniAppEnabled,
    miniAppUrl = miniAppUrl,
    miniAppPermissions = miniAppPermissions,
    commands = commands.map { AgentCommandData(it.command, it.description) },
    createdAt = createdAt
)
