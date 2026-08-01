package com.zula.features.groups

import com.zula.core.http.badRequest
import com.zula.features.groups.domain.CreateGroupRequest
import com.zula.features.groups.domain.UpdateGroupRequest
import com.zula.features.user.authenticateJwt
import com.zula.features.user.authenticateJwtOptional
import com.zula.features.user.optionalUserId
import com.zula.features.user.requireUserId
import com.zula.lib.id.Ids
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import org.koin.ktor.ext.inject

fun Route.configureGroupRouting() {
    val groupService: GroupService by inject()

    route("/groups") {
        authenticateJwt {
            post {
                val body = call.receive<CreateGroupRequest>()
                call.respond(groupService.create(call.requireUserId(), body))
            }.describe {
                operationId = "createGroup"
                tag("groups")
            }
        }

        authenticateJwtOptional {
            get("/{idOrSlug}") {
                val idOrSlug = call.parameters["idOrSlug"] ?: badRequest("idOrSlug required")
                call.respond(groupService.get(idOrSlug, call.optionalUserId()))
            }.describe {
                operationId = "getGroup"
                tag("groups")
            }
        }

        authenticateJwt {
            patch("/{id}") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                val body = call.receive<UpdateGroupRequest>()
                call.respond(groupService.patch(call.requireUserId(), id, body))
            }.describe {
                operationId = "updateGroup"
                tag("groups")
            }

            post("/{id}/join") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                call.respond(groupService.join(call.requireUserId(), id))
            }.describe {
                operationId = "joinGroup"
                tag("groups")
            }

            delete("/{id}/members/me") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                call.respond(groupService.leave(call.requireUserId(), id))
            }.describe {
                operationId = "leaveGroup"
                tag("groups")
            }

            get("/{id}/members") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val cursor = call.request.queryParameters["cursor"]?.let(Ids::parseOrNull)
                call.respond(
                    groupService.listMembers(
                        actorId = call.requireUserId(),
                        groupId = id,
                        cursorUserId = cursor,
                        limit = limit,
                    ),
                )
            }.describe {
                operationId = "listGroupMembers"
                tag("groups")
            }
        }
    }
}
