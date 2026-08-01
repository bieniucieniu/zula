package com.zula.features.user

import com.zula.core.http.badRequest
import com.zula.features.user.domain.PinPortfolioItemRequest
import com.zula.features.user.domain.ReorderProfilePinsRequest
import com.zula.features.user.domain.UpdateMyProfileRequest
import com.zula.features.user.domain.UpdateTrustRequest
import com.zula.features.user.domain.UpsertPortfolioItemRequest
import com.zula.lib.id.Ids
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import org.koin.ktor.ext.inject

fun Route.configureUserRouting() {
    val userService: UserService by inject()

    authenticateJwtOptional {
        get("/sellers/{idOrUsername}") {
            val idOrUsername = call.parameters["idOrUsername"] ?: badRequest("idOrUsername required")
            call.respond(userService.getSellerProfile(idOrUsername, call.optionalUserId()))
        }.describe {
            operationId = "getSellerProfile"
            tag("user")
        }

        get("/reviews/seller/{userId}") {
            val userId = call.parameters["userId"] ?: badRequest("userId required")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val cursor = call.request.queryParameters["cursor"]?.let { Ids.parseOrNull(it) }
                ?: call.request.queryParameters["id"]?.let { Ids.parseOrNull(it) }
            call.respond(
                userService.listSellerReviews(
                    sellerIdOrUsername = userId,
                    viewerId = call.optionalUserId(),
                    cursorId = cursor,
                    limit = limit,
                ),
            )
        }.describe {
            operationId = "listSellerReviews"
            tag("user")
        }

        get("/users/{id}/profile") {
            val id = call.parameters["id"] ?: badRequest("id required")
            call.respond(userService.getLegacyUserProfile(id, call.optionalUserId()))
        }.describe {
            operationId = "getUserProfile"
            tag("user")
        }

        get("/users/{idOrMe}/portfolio") {
            val idOrMe = call.parameters["idOrMe"] ?: badRequest("idOrMe required")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val cursor = call.request.queryParameters["cursor"]?.let { Ids.parseOrNull(it) }
            call.respond(
                userService.listPortfolio(
                    idOrMe = idOrMe,
                    viewerId = call.optionalUserId(),
                    cursorId = cursor,
                    limit = limit,
                ),
            )
        }.describe {
            operationId = "listPortfolioItems"
            tag("user")
        }

        get("/users/{idOrMe}/activity") {
            val idOrMe = call.parameters["idOrMe"] ?: badRequest("idOrMe required")
            call.respond(userService.listPublicActivity(idOrMe, call.optionalUserId()))
        }.describe {
            operationId = "listPublicActivity"
            tag("user")
        }
    }

    authenticateJwt {
        route("/users/me") {
            get("/profile") {
                call.respond(userService.getMyProfile(call.requireUserId()))
            }.describe {
                operationId = "getMyProfile"
                tag("user")
            }

            patch("/profile") {
                val body = call.receive<UpdateMyProfileRequest>()
                call.respond(userService.updateMyProfile(call.requireUserId(), body))
            }.describe {
                operationId = "updateMyProfile"
                tag("user")
            }

            route("/portfolio") {
                post("/items") {
                    val body = call.receive<UpsertPortfolioItemRequest>()
                    call.respond(userService.upsertPortfolioItem(call.requireUserId(), "me", body))
                }.describe {
                    operationId = "upsertPortfolioItem"
                    tag("user")
                }

                delete("/items/{itemId}") {
                    val itemId = call.parameters["itemId"]?.let(Ids::parseOrNull)
                        ?: badRequest("itemId must be a UUID")
                    call.respond(userService.deletePortfolioItem(call.requireUserId(), "me", itemId))
                }.describe {
                    operationId = "deletePortfolioItem"
                    tag("user")
                }

                post("/pins") {
                    val body = call.receive<PinPortfolioItemRequest>()
                    call.respond(userService.pinPortfolioItem(call.requireUserId(), "me", body))
                }.describe {
                    operationId = "pinPortfolioItem"
                    tag("user")
                }

                delete("/pins/{itemId}") {
                    val itemId = call.parameters["itemId"]?.let(Ids::parseOrNull)
                        ?: badRequest("itemId must be a UUID")
                    call.respond(userService.unpinPortfolioItem(call.requireUserId(), "me", itemId))
                }.describe {
                    operationId = "unpinPortfolioItem"
                    tag("user")
                }

                put("/pins/reorder") {
                    val body = call.receive<ReorderProfilePinsRequest>()
                    call.respond(userService.reorderProfilePins(call.requireUserId(), "me", body))
                }.describe {
                    operationId = "reorderProfilePins"
                    tag("user")
                }
            }
        }

        post("/users/{userId}/block") {
            val target = call.parameters["userId"]?.let(Ids::parseOrNull)
                ?: badRequest("userId must be a UUID")
            call.respond(userService.blockUser(call.requireUserId(), target))
        }.describe {
            operationId = "blockUser"
            tag("user")
        }

        delete("/users/{userId}/block") {
            val target = call.parameters["userId"]?.let(Ids::parseOrNull)
                ?: badRequest("userId must be a UUID")
            call.respond(userService.unblockUser(call.requireUserId(), target))
        }.describe {
            operationId = "unblockUser"
            tag("user")
        }

        patch("/admin/users/{id}/trust") {
            val target = call.parameters["id"]?.let(Ids::parseOrNull)
                ?: badRequest("id must be a UUID")
            val body = call.receive<UpdateTrustRequest>()
            call.respond(userService.updateImplicitTrust(call.requireUserId(), target, body))
        }.describe {
            operationId = "updateImplicitTrust"
            tag("user")
        }
    }

    // Legacy trust-only lookup — after /users/me and /users/{id}/profile.
    authenticateJwtOptional {
        get("/users/{username}") {
            val username = call.parameters["username"] ?: badRequest("username required")
            if (username == "me") {
                badRequest("Use /users/me/profile")
            }
            call.respond(userService.getLegacyUserProfile(username, call.optionalUserId()))
        }.describe {
            operationId = "getUserByUsername"
            tag("user")
        }
    }
}
