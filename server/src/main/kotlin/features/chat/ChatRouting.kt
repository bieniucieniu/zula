package com.zula.features.chat

import com.zula.core.http.badRequest
import com.zula.features.chat.domain.SendMessageRequest
import com.zula.features.user.authenticateJwt
import com.zula.features.user.requireUserId
import com.zula.lib.id.Ids
import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.collect
import org.koin.ktor.ext.inject

fun Route.configureChatRouting() {
    val chatService: ChatService by inject()
    val sseHub: SseEventHub by inject()

    authenticateJwt {
        sse("/events/stream") {
            val userId = call.requireUserId()
            val lastEventId = call.request.header(HttpHeaders.LastEventID)
                ?.let { Ids.parseOrNull(it) }
            if (lastEventId != null) {
                sseHub.replayAfter(userId, lastEventId).forEach { event ->
                    send(
                        ServerSentEvent(
                            data = event.payloadJson,
                            event = event.type,
                            id = event.id.toString(),
                        ),
                    )
                }
            }
            sseHub.subscribe(userId).collect { event ->
                send(
                    ServerSentEvent(
                        data = event.payloadJson,
                        event = event.type,
                        id = event.id.toString(),
                    ),
                )
            }
        }

        route("/chat") {
            get("/rooms/{id}") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                call.respond(chatService.getRoom(id, call.requireUserId()))
            }.describe {
                operationId = "getChatRoom"
                tag("chat")
            }

            get("/rooms/by-trade/{tradeId}") {
                val tradeId = call.parameters["tradeId"]?.let(Ids::parseOrNull)
                    ?: badRequest("tradeId required")
                call.respond(chatService.getRoomByTrade(tradeId, call.requireUserId()))
            }.describe {
                operationId = "getChatRoomByTrade"
                tag("chat")
            }

            get("/rooms/by-group/{groupId}") {
                val groupId = call.parameters["groupId"]?.let(Ids::parseOrNull)
                    ?: badRequest("groupId required")
                call.respond(chatService.getRoomByGroup(groupId, call.requireUserId()))
            }.describe {
                operationId = "getChatRoomByGroup"
                tag("chat")
            }

            get("/rooms/{id}/messages") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val cursor = call.request.queryParameters["cursor"]?.let(Ids::parseOrNull)
                call.respond(
                    chatService.listMessages(id, call.requireUserId(), cursor, limit),
                )
            }.describe {
                operationId = "listChatMessages"
                tag("chat")
            }

            post("/rooms/{id}/messages") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                val body = call.receive<SendMessageRequest>()
                call.respond(chatService.sendMessage(id, call.requireUserId(), body))
            }.describe {
                operationId = "sendChatMessage"
                tag("chat")
            }
        }
    }
}
