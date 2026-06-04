package com.pinealctx.nexus.di

import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.core.managers.AgentManager
import com.pinealctx.nexus.core.managers.AuthManager
import com.pinealctx.nexus.core.managers.ContactManager
import com.pinealctx.nexus.core.managers.ConversationManager
import com.pinealctx.nexus.core.managers.GroupManager
import com.pinealctx.nexus.core.managers.MediaManager
import com.pinealctx.nexus.core.managers.MessageManager
import com.pinealctx.nexus.core.managers.PushManager
import com.pinealctx.nexus.core.managers.SearchManager
import com.pinealctx.nexus.core.managers.SyncBridge
import com.pinealctx.nexus.core.managers.UserManager
import com.pinealctx.nexus.client.AgentApi
import com.pinealctx.nexus.client.AuthApi
import com.pinealctx.nexus.client.ContactApi
import com.pinealctx.nexus.client.ConversationApi
import com.pinealctx.nexus.client.GatewayClient
import com.pinealctx.nexus.client.GroupApi
import com.pinealctx.nexus.client.MediaApi
import com.pinealctx.nexus.client.MessageApi
import com.pinealctx.nexus.client.PushApi
import com.pinealctx.nexus.client.SyncEngine
import com.pinealctx.nexus.client.UserApi
import com.pinealctx.nexus.local.LocalDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ManagerModule {

    @Provides
    @Singleton
    fun provideAuthManager(
        authApi: AuthApi,
        secureStorage: SecureStorage
    ): AuthManager =
        AuthManager(authApi, secureStorage)

    @Provides
    @Singleton
    fun provideConversationManager(
        conversationApi: ConversationApi,
        localDataStore: LocalDataStore
    ): ConversationManager =
        ConversationManager(conversationApi, localDataStore)

    @Provides
    @Singleton
    fun provideMessageManager(
        messageApi: MessageApi,
        localDataStore: LocalDataStore,
        secureStorage: SecureStorage
    ): MessageManager =
        MessageManager(messageApi, localDataStore, secureStorage)

    @Provides
    @Singleton
    fun provideContactManager(
        contactApi: ContactApi,
        localDataStore: LocalDataStore
    ): ContactManager =
        ContactManager(contactApi, localDataStore)

    @Provides
    @Singleton
    fun provideGroupManager(
        groupApi: GroupApi,
        localDataStore: LocalDataStore
    ): GroupManager =
        GroupManager(groupApi, localDataStore)

    @Provides
    @Singleton
    fun provideMediaManager(
        mediaApi: MediaApi,
        localDataStore: LocalDataStore
    ): MediaManager =
        MediaManager(mediaApi, localDataStore)

    @Provides
    @Singleton
    fun provideAgentManager(
        agentApi: AgentApi,
        localDataStore: LocalDataStore
    ): AgentManager =
        AgentManager(agentApi, localDataStore)

    @Provides
    @Singleton
    fun provideSearchManager(
        contactApi: ContactApi,
        localDataStore: LocalDataStore
    ): SearchManager =
        SearchManager(contactApi, localDataStore)

    @Provides
    @Singleton
    fun provideUserManager(
        userApi: UserApi,
        localDataStore: LocalDataStore
    ): UserManager =
        UserManager(userApi, localDataStore)

    @Provides
    @Singleton
    fun providePushManager(pushApi: PushApi): PushManager =
        PushManager(pushApi)

    @Provides
    @Singleton
    fun provideSyncBridge(
        gatewayClient: GatewayClient,
        syncEngine: SyncEngine
    ): SyncBridge =
        SyncBridge(gatewayClient, syncEngine)
}
