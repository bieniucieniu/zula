package com.zula.features.chat

import com.zula.Chat_messages
import com.zula.Chat_rooms
import com.zula.core.http.badRequest
import com.zula.core.http.forbidden
import com.zula.core.http.notFound
import com.zula.features.chat.domain.ChatMessageResponse
import com.zula.features.chat.domain.ChatRoomResponse
import com.zula.features.chat.domain.ListMessagesResponse
import com.zula.features.chat.domain.MessageReceivedPayload
import com.zula.features.chat.domain.SendMessageRequest
import com.zula.features.chat.domain.SseEventTypes
import com.zula.lib.id.Ids
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.uuid.Uuid

class ChatService(
    private val repository: ChatRepository,
    private val sse: SseEventSink,
    private val json: Json,
) : ChatRoomEnsurer {
    override fun ensureTradeRoom(tradeId: Uuid, participantIds: Collection<Uuid>): Chat_rooms {
        repository.getRoomByTradeId(tradeId)?.let { existing ->
            addMissingParticipants(existing.id, participantIds)
            return existing
        }
        val now = Instant.now().epochSecond
        return repository.transaction {
            val room = insertRoom(tradeId = tradeId, groupId = null, createdAt = now)
            participantIds.distinct().forEach { uid ->
                insertParticipant(room.id, uid, now)
            }
            room
        }
    }

    override fun ensureGroupRoom(groupId: Uuid, memberIds: Collection<Uuid>): Chat_rooms {
        val existing = repository.getRoomByGroupId(groupId)
        if (existing != null) {
            addMissingParticipants(existing.id, memberIds)
            return existing
        }
        val now = Instant.now().epochSecond
        return repository.transaction {
            val room = insertRoom(tradeId = null, groupId = groupId, createdAt = now)
            memberIds.distinct().forEach { uid ->
                insertParticipant(room.id, uid, now)
            }
            room
        }
    }

    fun getRoom(roomId: Uuid, viewerId: Uuid): ChatRoomResponse {
        val room = repository.getRoomById(roomId) ?: notFound("Chat room not found")
        requireParticipant(room.id, viewerId)
        return toRoomResponse(room)
    }

    fun getRoomByTrade(tradeId: Uuid, viewerId: Uuid): ChatRoomResponse {
        val room = repository.getRoomByTradeId(tradeId) ?: notFound("Chat room not found")
        requireParticipant(room.id, viewerId)
        return toRoomResponse(room)
    }

    fun getRoomByGroup(groupId: Uuid, viewerId: Uuid): ChatRoomResponse {
        val room = repository.getRoomByGroupId(groupId) ?: notFound("Chat room not found")
        requireParticipant(room.id, viewerId)
        return toRoomResponse(room)
    }

    fun listMessages(
        roomId: Uuid,
        viewerId: Uuid,
        cursorId: Uuid?,
        limit: Int,
    ): ListMessagesResponse {
        requireParticipant(roomId, viewerId)
        val capped = limit.coerceIn(1, 100)
        val rows = if (cursorId == null) {
            repository.listMessagesFirst(roomId, capped.toLong() + 1)
        } else {
            repository.listMessagesPage(roomId, cursorId, capped.toLong() + 1)
        }
        val hasMore = rows.size > capped
        val page = if (hasMore) rows.take(capped) else rows
        return ListMessagesResponse(
            messages = page.map { it.toResponse() },
            nextCursor = page.lastOrNull()?.id?.toString()?.takeIf { hasMore },
            hasMore = hasMore,
        )
    }

    fun sendMessage(roomId: Uuid, senderId: Uuid, req: SendMessageRequest): ChatMessageResponse {
        requireParticipant(roomId, senderId)
        val body = req.body.trim()
        if (body.isEmpty()) badRequest("body required")
        if (body.length > MAX_BODY_CHARS) badRequest("body must be at most $MAX_BODY_CHARS characters")
        val clientMessageId = req.clientMessageId.trim()
        if (clientMessageId.isEmpty()) badRequest("clientMessageId required")
        if (clientMessageId.length > 128) badRequest("clientMessageId too long")

        repository.getMessageByClientId(roomId, senderId, clientMessageId)?.let {
            return it.toResponse()
        }

        val message = repository.insertMessage(roomId, senderId, body, clientMessageId)
        val payload = json.encodeToString(
            MessageReceivedPayload(
                roomId = roomId.toString(),
                messageId = message.id.toString(),
                senderId = senderId.toString(),
                body = body,
                clientMessageId = clientMessageId,
            ),
        )
        repository.listParticipantIds(roomId).forEach { uid ->
            sse.emit(uid, SseEventTypes.MESSAGE_RECEIVED, payload)
        }
        return message.toResponse()
    }

    private fun addMissingParticipants(roomId: Uuid, userIds: Collection<Uuid>) {
        val now = Instant.now().epochSecond
        userIds.distinct().forEach { uid ->
            if (!repository.isParticipant(roomId, uid)) {
                repository.insertParticipant(roomId, uid, now)
            }
        }
    }

    private fun requireParticipant(roomId: Uuid, userId: Uuid) {
        if (!repository.isParticipant(roomId, userId)) {
            forbidden("Not a chat room participant")
        }
    }

    private fun toRoomResponse(room: Chat_rooms): ChatRoomResponse =
        ChatRoomResponse(
            id = room.id.toString(),
            tradeId = room.trade_id?.toString(),
            groupId = room.group_id?.toString(),
            createdAt = room.created_at,
            participantIds = repository.listParticipantIds(room.id).map { it.toString() },
        )

    private fun Chat_messages.toResponse() = ChatMessageResponse(
        id = id.toString(),
        roomId = room_id.toString(),
        senderId = sender_id.toString(),
        body = body,
        clientMessageId = client_message_id,
        createdAt = Ids.createdAtMillis(id) / 1000,
    )

    companion object {
        const val MAX_BODY_CHARS = 4096
    }
}
