package com.zula.features.chat

import com.zula.Chat_messages
import com.zula.Chat_rooms
import com.zula.Database
import com.zula.Sse_event_log
import kotlin.uuid.Uuid

class ChatRepository(
    private val database: Database,
) {
    private val queries inline get() = database.chatQueries

    fun <T> transaction(block: ChatRepository.() -> T): T =
        database.transactionWithResult { this@ChatRepository.block() }

    fun insertRoom(tradeId: Uuid?, groupId: Uuid?, createdAt: Long): Chat_rooms =
        queries.insertChatRoom(tradeId, groupId, createdAt).executeAsOne()

    fun getRoomById(roomId: Uuid): Chat_rooms? =
        queries.getRoomById(roomId).executeAsOneOrNull()

    fun getRoomByTradeId(tradeId: Uuid): Chat_rooms? =
        queries.getRoomByTradeId(tradeId).executeAsOneOrNull()

    fun getRoomByGroupId(groupId: Uuid): Chat_rooms? =
        queries.getRoomByGroupId(groupId).executeAsOneOrNull()

    fun insertParticipant(roomId: Uuid, userId: Uuid, joinedAt: Long) {
        queries.insertChatParticipant(roomId, userId, joinedAt)
    }

    fun isParticipant(roomId: Uuid, userId: Uuid): Boolean =
        queries.isRoomParticipant(roomId, userId).executeAsOne() > 0

    fun listParticipantIds(roomId: Uuid): List<Uuid> =
        queries.listRoomParticipants(roomId).executeAsList()

    fun insertMessage(
        roomId: Uuid,
        senderId: Uuid,
        body: String,
        clientMessageId: String,
    ): Chat_messages =
        queries.insertMessage(roomId, senderId, body, clientMessageId).executeAsOne()

    fun getMessageByClientId(
        roomId: Uuid,
        senderId: Uuid,
        clientMessageId: String,
    ): Chat_messages? =
        queries.getMessageByClientId(roomId, senderId, clientMessageId).executeAsOneOrNull()

    fun listMessagesFirst(roomId: Uuid, limit: Long): List<Chat_messages> =
        queries.listMessagesFirst(roomId, limit).executeAsList()

    fun listMessagesPage(roomId: Uuid, beforeId: Uuid, limit: Long): List<Chat_messages> =
        queries.listMessagesPage(roomId, beforeId, limit).executeAsList()

    fun insertSseEvent(
        userId: Uuid,
        eventType: String,
        payload: String,
        createdAt: Long,
    ): Sse_event_log =
        queries.insertSseEvent(userId, eventType, payload, createdAt).executeAsOne()

    fun listSseEventsAfter(userId: Uuid, afterId: Uuid, limit: Long): List<Sse_event_log> =
        queries.listSseEventsAfter(userId, afterId, limit).executeAsList()
}
