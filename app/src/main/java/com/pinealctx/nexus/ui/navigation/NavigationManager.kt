package com.pinealctx.nexus.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class NavigationEvent {
    data class ToChat(val conversationId: String, val highlightMessageId: Long? = null) : NavigationEvent()
    data class ToGroupDetail(val groupId: Int) : NavigationEvent()
    object ToLogin : NavigationEvent()
    object ToFriendRequests : NavigationEvent()
    object ToSearch : NavigationEvent()
    object ToSettings : NavigationEvent()
    object Back : NavigationEvent()
}

@Singleton
class NavigationManager @Inject constructor() {

    private val _events = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<NavigationEvent> = _events.asSharedFlow()

    fun navigate(event: NavigationEvent) {
        _events.tryEmit(event)
    }

    fun toChat(conversationId: String, highlightMessageId: Long? = null) {
        navigate(NavigationEvent.ToChat(conversationId, highlightMessageId))
    }

    fun toLogin() {
        navigate(NavigationEvent.ToLogin)
    }

    fun back() {
        navigate(NavigationEvent.Back)
    }
}
