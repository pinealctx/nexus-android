package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.AgentApi
import com.pinealctx.nexus.core.AgentInfoData
import com.pinealctx.nexus.core.MiniAppLaunchResult
import com.pinealctx.nexus.local.LocalDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentManager @Inject constructor(
    private val agentApi: AgentApi,
    private val localDataStore: LocalDataStore
) {
    suspend fun listFeaturedAgents(limit: Int = 50): List<AgentInfoData> {
        val cached = localDataStore.listFeaturedAgents(limit)
        if (cached.isNotEmpty()) return cached

        return agentApi.listFeaturedAgents(limit)
            .also { localDataStore.upsertAgents(it, featured = true) }
    }

    suspend fun getAgentInfo(agentUserId: Int): AgentInfoData? {
        return localDataStore.getAgent(agentUserId)
            ?: agentApi.getAgentInfo(agentUserId)
                ?.also { localDataStore.upsertAgent(it) }
    }

    suspend fun getMiniAppLaunchData(
        agentUserId: Int,
        conversationId: Long,
        startParam: String = ""
    ): MiniAppLaunchResult = agentApi.getMiniAppLaunchData(agentUserId, conversationId, startParam)

    suspend fun createAgent(username: String, nickname: String, description: String): Int {
        val agentUserId = agentApi.createAgent(username, nickname, description)
        agentApi.getAgentInfo(agentUserId)
            ?.also { localDataStore.upsertAgent(it, mine = true) }
        return agentUserId
    }

    suspend fun listMyAgents(): List<AgentInfoData> {
        val cached = localDataStore.listMyAgents()
        if (cached.isNotEmpty()) return cached

        return agentApi.listMyAgents()
            .also { localDataStore.upsertAgents(it, mine = true) }
    }
}
