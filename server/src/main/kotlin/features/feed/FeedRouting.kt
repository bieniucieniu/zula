package com.zula.features.feed

import com.zula.core.http.badRequest
import com.zula.features.feed.domain.CreateCommentRequest
import com.zula.features.feed.domain.CreateFeedItemRequest
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
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import org.koin.ktor.ext.inject

fun Route.configureFeedRouting() {
    val feedService: FeedService by inject()

    route("/feed") {
        get("/traits") {
            call.respond(feedService.listTraits())
        }.describe {
            operationId = "listTraits"
            tag("feed")
        }

        authenticateJwtOptional {
            get {
                call.respond(
                    feedService.listChrono(
                        viewerId = call.optionalUserId(),
                        cursorId = call.cursorId(),
                        limit = call.limit(),
                    ),
                )
            }.describe {
                operationId = "listFeed"
                tag("feed")
            }

            get("/for-you") {
                call.respond(
                    feedService.listForYou(
                        viewerId = call.optionalUserId(),
                        cursorId = call.cursorId(),
                        limit = call.limit(),
                    ),
                )
            }.describe {
                operationId = "listForYouFeed"
                tag("feed")
            }

            get("/items/{id}") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                call.respond(feedService.getItem(id, call.optionalUserId()))
            }.describe {
                operationId = "getFeedItem"
                tag("feed")
            }

            get("/by-author/{userId}") {
                val userId = call.parameters["userId"]?.let(Ids::parseOrNull)
                    ?: badRequest("userId must be a UUID")
                call.respond(
                    feedService.listByAuthor(
                        authorId = userId,
                        viewerId = call.optionalUserId(),
                        cursorId = call.cursorId(),
                        limit = call.limit(),
                    ),
                )
            }.describe {
                operationId = "listFeedByAuthor"
                tag("feed")
            }

            get("/by-trait/{traitId}") {
                val traitId = call.parameters["traitId"]?.let(Ids::parseOrNull)
                    ?: badRequest("traitId must be a UUID")
                call.respond(
                    feedService.listByTrait(
                        traitId = traitId,
                        viewerId = call.optionalUserId(),
                        cursorId = call.cursorId(),
                        limit = call.limit(),
                    ),
                )
            }.describe {
                operationId = "listFeedByTrait"
                tag("feed")
            }

            get("/by-group/{groupId}") {
                val groupId = call.parameters["groupId"]?.let(Ids::parseOrNull)
                    ?: badRequest("groupId must be a UUID")
                call.respond(
                    feedService.listByGroup(
                        groupId = groupId,
                        viewerId = call.optionalUserId(),
                        cursorId = call.cursorId(),
                        limit = call.limit(),
                    ),
                )
            }.describe {
                operationId = "listFeedByGroup"
                tag("feed")
            }

            get("/items/{id}/comments") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                call.respond(
                    feedService.listComments(
                        itemId = id,
                        viewerId = call.optionalUserId(),
                        cursorId = call.cursorId(),
                        limit = call.limit(),
                    ),
                )
            }.describe {
                operationId = "listComments"
                tag("feed")
            }
        }

        authenticateJwt {
            post("/items") {
                val body = call.receive<CreateFeedItemRequest>()
                call.respond(feedService.createItem(call.requireUserId(), body))
            }.describe {
                operationId = "createFeedItem"
                tag("feed")
            }

            post("/items/{id}/likes") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                call.respond(feedService.like(call.requireUserId(), id))
            }.describe {
                operationId = "likeFeedItem"
                tag("feed")
            }

            delete("/items/{id}/likes") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                call.respond(feedService.unlike(call.requireUserId(), id))
            }.describe {
                operationId = "unlikeFeedItem"
                tag("feed")
            }

            post("/items/{id}/comments") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                val body = call.receive<CreateCommentRequest>()
                call.respond(feedService.addComment(call.requireUserId(), id, body))
            }.describe {
                operationId = "addComment"
                tag("feed")
            }

            post("/items/{id}/bookmarks") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                call.respond(feedService.bookmark(call.requireUserId(), id))
            }.describe {
                operationId = "bookmarkFeedItem"
                tag("feed")
            }

            delete("/items/{id}/bookmarks") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id must be a UUID")
                call.respond(feedService.unbookmark(call.requireUserId(), id))
            }.describe {
                operationId = "unbookmarkFeedItem"
                tag("feed")
            }

            get("/bookmarks") {
                call.respond(
                    feedService.listBookmarks(
                        userId = call.requireUserId(),
                        cursorId = call.cursorId(),
                        limit = call.limit(),
                    ),
                )
            }.describe {
                operationId = "listBookmarks"
                tag("feed")
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.limit(): Int =
    request.queryParameters["limit"]?.toIntOrNull() ?: 20

private fun io.ktor.server.application.ApplicationCall.cursorId() =
    request.queryParameters["cursor"]?.let(Ids::parseOrNull)
