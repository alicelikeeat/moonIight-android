package com.moonlight.feed.data.social

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Result of handing a message to a transport. */
data class SendResult(val status: MessageStatus, val serverAt: Long? = null, val remoteId: String? = null, val error: String? = null)

/** Server → client events (see docs/CHAT_PROTOCOL.md). */
sealed interface TransportEvent {
    data class Incoming(val message: Message) : TransportEvent
    data class Receipt(val messageId: String, val status: MessageStatus, val at: Long) : TransportEvent
    data class ConversationUpdated(val conversation: Conversation) : TransportEvent
    data class ConnectionChanged(val connected: Boolean, val reason: String? = null) : TransportEvent
}

data class SyncResult(val messages: List<Message>, val conversations: List<Conversation>, val cursor: String?)

/**
 * Where chat messages go. [LocalTransport] keeps today's offline/virtual behaviour;
 * [RemoteTransport] is the (inactive) shape of a future server connection.
 */
interface ChatTransport {
    val id: String
    val active: Boolean
    suspend fun send(message: Message, conversation: Conversation): SendResult
    fun events(): Flow<TransportEvent>
    suspend fun sync(cursor: String?): SyncResult
    suspend fun markRead(conversationId: String, upToMessageId: String)
}

/** Offline: the message is final as soon as it is stored on the device. */
class LocalTransport : ChatTransport {
    override val id = "local"
    override val active = true
    override suspend fun send(message: Message, conversation: Conversation) = SendResult(MessageStatus.SENT)
    override fun events(): Flow<TransportEvent> = emptyFlow()
    override suspend fun sync(cursor: String?) = SyncResult(emptyList(), emptyList(), cursor)
    override suspend fun markRead(conversationId: String, upToMessageId: String) = Unit
}

data class ChatServerConfig(val baseUrl: String = "", val token: String? = null, val userId: String? = null) {
    val configured get() = baseUrl.isNotBlank()
    fun wsUrl(): String = baseUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + RemoteTransport.WS_PATH
}

/**
 * Stub of the future server transport (HTTP + WebSocket, see docs/CHAT_PROTOCOL.md).
 * NOT active: no network calls are made; every send fails with "即将推出".
 */
class RemoteTransport(val config: ChatServerConfig) : ChatTransport {
    override val id = "remote"
    override val active = false
    override suspend fun send(message: Message, conversation: Conversation) =
        SendResult(MessageStatus.FAILED, error = "聊天服务器即将推出，消息仅保存在本机")
    override fun events(): Flow<TransportEvent> = emptyFlow()
    override suspend fun sync(cursor: String?) = SyncResult(emptyList(), emptyList(), cursor)
    override suspend fun markRead(conversationId: String, upToMessageId: String) = Unit

    companion object {
        const val API = "/v1"
        const val AUTH = "$API/auth/token"
        const val ME = "$API/me"
        const val CONVERSATIONS = "$API/conversations"
        fun messages(convId: String) = "$API/conversations/$convId/messages"
        fun receipts(convId: String) = "$API/conversations/$convId/receipts"
        const val SYNC = "$API/sync"
        const val UPLOADS = "$API/uploads"
        const val WS_PATH = "$API/ws"
    }
}
