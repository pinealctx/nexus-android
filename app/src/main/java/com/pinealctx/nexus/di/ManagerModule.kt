package com.pinealctx.nexus.di

import com.pinealctx.nexus.core.NexusClientProvider
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
    fun provideAuthManager(clientProvider: NexusClientProvider): AuthManager =
        AuthManager(clientProvider)

    @Provides
    @Singleton
    fun provideConversationManager(clientProvider: NexusClientProvider): ConversationManager =
        ConversationManager(clientProvider)

    @Provides
    @Singleton
    fun provideMessageManager(clientProvider: NexusClientProvider): MessageManager =
        MessageManager(clientProvider)

    @Provides
    @Singleton
    fun provideContactManager(clientProvider: NexusClientProvider): ContactManager =
        ContactManager(clientProvider)

    @Provides
    @Singleton
    fun provideGroupManager(clientProvider: NexusClientProvider): GroupManager =
        GroupManager(clientProvider)

    @Provides
    @Singleton
    fun provideMediaManager(clientProvider: NexusClientProvider): MediaManager =
        MediaManager(clientProvider)

    @Provides
    @Singleton
    fun provideAgentManager(clientProvider: NexusClientProvider): AgentManager =
        AgentManager(clientProvider)

    @Provides
    @Singleton
    fun provideSearchManager(clientProvider: NexusClientProvider): SearchManager =
        SearchManager(clientProvider)

    @Provides
    @Singleton
    fun provideUserManager(clientProvider: NexusClientProvider): UserManager =
        UserManager(clientProvider)

    @Provides
    @Singleton
    fun providePushManager(clientProvider: NexusClientProvider): PushManager =
        PushManager(clientProvider)

    @Provides
    @Singleton
    fun provideSyncBridge(clientProvider: NexusClientProvider): SyncBridge =
        SyncBridge(clientProvider)
}
