package com.zula.features.chat.domain

import kotlinx.serialization.Serializable

@Serializable
data class ChatRoomResponse(
    val id: String,
    val tradeId: String? = null,
    val groupId: String? = null,
    val createdAt: Long,
    val participantIds: List<String> = emptyList(),
)

@Serializable
data class ChatMessageResponse(
    val id: String,
    val roomId: String,
    val senderId: String,
    val body: String,
    val clientMessageId: String,
    val createdAt: Long,
)

@Serializable
data class ListMessagesResponse(
    val messages: List<ChatMessageResponse>,
    val nextCursor: String? = null,
    val hasMore: Boolean,
)

@Serializable
data class SendMessageRequest(
    val body: String,
    val clientMessageId: String,
)

@Serializable
data class MessageReceivedPayload(
    val roomId: String,
    val messageId: String,
    val senderId: String,
    val body: String,
    val clientMessageId: String,
)

object SseEventTypes {
    const val MESSAGE_RECEIVED = "MESSAGE_RECEIVED"
    const val TRADE_STATE_CHANGED = "TRADE_STATE_CHANGED"
    /** Reserved post-MVP */
    const val PAYMENT_SUCCESS = "PAYMENT_SUCCESS"
    /** Reserved post-MVP */
    const val PAYMENT_FAILED = "PAYMENT_FAILED"
}
