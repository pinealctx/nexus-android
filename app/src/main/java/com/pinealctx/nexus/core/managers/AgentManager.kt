package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.AgentApi
import com.pinealctx.nexus.core.AgentInfoData
import com.pinealctx.nexus.core.MiniAppLaunchResult
import com.pinealctx.nexus.local.LocalDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class AgentManager @Inject constructor(
    private val agentApi: AgentApi,
    private val localDataStore: LocalDataStore
) {
    fun listFeaturedAgents(limit: Int = 50): List<AgentInfoData> {
        val cached = localDataStore.listFeaturedAgents(limit)
        if (cached.isNotEmpty()) return cached

        return runBlocking {
            agentApi.listFeaturedAgents(limit)
                .also { localDataStore.upsertAgents(it, featured = true) }
        }
    }

    fun getAgentInfo(agentUserId: Int): AgentInfoData? {
        return localDataStore.getAgent(agentUserId)
            ?: runBlocking {
                agentApi.getAgentInfo(agentUserId)
                    ?.also { localDataStore.upsertAgent(it) }
            }
    }

    fun getMiniAppLaunchData(agentUserId: Int, conversationId: Long, startParam: String = ""): MiniAppLaunchResult {
        return runBlocking { agentApi.getMiniAppLaunchData(agentUserId, conversationId, startParam) }
    }

    fun createAgent(username: String, nickname: String, description: String): Int {
        val agentUserId = runBlocking { agentApi.createAgent(username, nickname, description) }
        runBlocking {
            agentApi.getAgentInfo(agentUserId)
                ?.also { localDataStore.upsertAgent(it, mine = true) }
        }
        return agentUserId
    }

    fun listMyAgents(): List<AgentInfoData> {
        val cached = localDataStore.listMyAgents()
        if (cached.isNotEmpty()) return cached

        return runBlocking {
            agentApi.listMyAgents()
                .also { localDataStore.upsertAgents(it, mine = true) }
        }
    }
}
