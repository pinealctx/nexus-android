package com.pinealctx.nexus.client

import com.api.v1.AgentServiceClient
import com.api.v1.AuthServiceClient
import com.api.v1.ContactServiceClient
import com.api.v1.ConversationServiceClient
import com.api.v1.GroupServiceClient
import com.api.v1.MediaServiceClient
import com.api.v1.MessageServiceClient
import com.api.v1.PushServiceClient
import com.api.v1.SyncServiceClient
import com.api.v1.UserServiceClient
import com.connectrpc.ProtocolClientInterface

class ApiClients(protocolClient: ProtocolClientInterface) {
    val auth = AuthServiceClient(protocolClient)
    val conversations = ConversationServiceClient(protocolClient)
    val messages = MessageServiceClient(protocolClient)
    val contacts = ContactServiceClient(protocolClient)
    val groups = GroupServiceClient(protocolClient)
    val media = MediaServiceClient(protocolClient)
    val agents = AgentServiceClient(protocolClient)
    val users = UserServiceClient(protocolClient)
    val push = PushServiceClient(protocolClient)
    val sync = SyncServiceClient(protocolClient)
}
