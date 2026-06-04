package com.pinealctx.nexus.client

import com.api.v1.CreateAgentRequest
import com.api.v1.GetAgentInfoRequest
import com.api.v1.GetMiniAppLaunchDataRequest
import com.api.v1.ListFeaturedAgentsRequest
import com.api.v1.ListMyAgentsRequest
import com.pinealctx.nexus.core.AgentCommandData
import com.pinealctx.nexus.core.AgentInfoData
import com.pinealctx.nexus.core.MiniAppLaunchResult
import com.shared.v1.AgentCommandInfo
import com.shared.v1.AgentInfo
import com.shared.v1.AgentProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentApi @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val headers: RpcHeaders
) {
    suspend fun listFeaturedAgents(limit: Int = 50): List<AgentInfoData> {
        return apiClientFactory.createClients()
            .agents
            .listFeaturedAgents(
                request = ListFeaturedAgentsRequest.newBuilder()
                    .setLimit(limit)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
            .agentsList
            .map { it.toData() }
    }

    suspend fun getAgentInfo(agentUserId: Int): AgentInfoData? {
        val response = apiClientFactory.createClients()
            .agents
            .getAgentInfo(
                request = GetAgentInfoRequest.newBuilder()
                    .setAgentUserId(agentUserId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return if (response.hasAgent()) response.agent.toData() else null
    }

    suspend fun getMiniAppLaunchData(
        agentUserId: Int,
        conversationId: Long,
        startParam: String = ""
    ): MiniAppLaunchResult {
        val response = apiClientFactory.createClients()
            .agents
            .getMiniAppLaunchData(
                request = GetMiniAppLaunchDataRequest.newBuilder()
                    .setAgentUserId(agentUserId)
                    .setConversationId(conversationId)
                    .setStartParam(startParam)
                    .setPlatform("android")
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return MiniAppLaunchResult(response.initData, response.miniAppUrl)
    }

    suspend fun createAgent(username: String, nickname: String, description: String): Int {
        val response = apiClientFactory.createClients()
            .agents
            .createAgent(
                request = CreateAgentRequest.newBuilder()
                    .setUsername(username)
                    .setName(nickname)
                    .setSignature(description)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return response.profile.user.userId
    }

    suspend fun listMyAgents(): List<AgentInfoData> {
        return apiClientFactory.createClients()
            .agents
            .listMyAgents(ListMyAgentsRequest.getDefaultInstance(), headers.current())
            .requireMessage()
            .agentsList
            .map { it.toData() }
    }
}

private fun AgentInfo.toData(): AgentInfoData {
    return AgentInfoData(
        userId = user.userId,
        username = user.username,
        nickname = user.nickname,
        avatarUrl = user.avatarUrl,
        signature = user.signature,
        isSystemAgent = isSystemAgent,
        miniAppEnabled = miniAppEnabled,
        miniAppUrl = miniAppUrl,
        miniAppPermissions = miniAppPermissions,
        commands = commandsList.map { it.toData() },
        createdAt = createdAt,
        status = status.number
    )
}

private fun AgentProfile.toData(): AgentInfoData {
    return AgentInfoData(
        userId = user.userId,
        username = user.username,
        nickname = user.nickname,
        avatarUrl = user.avatarUrl,
        signature = user.signature,
        isSystemAgent = isSystemAgent,
        miniAppEnabled = miniAppEnabled,
        miniAppUrl = miniAppUrl,
        miniAppPermissions = miniAppPermissions,
        commands = commandsList.map { it.toData() },
        createdAt = createdAt,
        status = status.number
    )
}

private fun AgentCommandInfo.toData(): AgentCommandData =
    AgentCommandData(command = command, description = description)
