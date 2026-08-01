package com.zula.features.chat

import com.zula.Chat_rooms
import kotlin.uuid.Uuid

/** Contract for trade/groups to ensure a chat room exists for a context. */
interface ChatRoomEnsurer {
    fun ensureTradeRoom(tradeId: Uuid, participantIds: Collection<Uuid>): Chat_rooms

    fun ensureGroupRoom(groupId: Uuid, memberIds: Collection<Uuid>): Chat_rooms
}
