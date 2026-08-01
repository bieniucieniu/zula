package com.zula.features.trade

import com.zula.core.http.badRequest
import com.zula.features.trade.domain.CreateTradeRequest
import com.zula.features.trade.domain.SetFulfillmentPlaceRequest
import com.zula.features.trade.domain.SetLocationModeRequest
import com.zula.features.user.authenticateJwt
import com.zula.features.user.requireUserId
import com.zula.lib.id.Ids
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import org.koin.ktor.ext.inject

fun Route.configureTradeRouting() {
    val tradeService: TradeService by inject()

    authenticateJwt {
        route("/trades") {
            post {
                val body = call.receive<CreateTradeRequest>()
                call.respond(tradeService.createTrade(call.requireUserId(), body))
            }.describe {
                operationId = "createTrade"
                tag("trade")
            }

            get("/{id}") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                call.respond(tradeService.getTrade(id, call.requireUserId()))
            }.describe {
                operationId = "getTrade"
                tag("trade")
            }

            post("/{id}/accept") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                call.respond(tradeService.acceptTrade(id, call.requireUserId()))
            }.describe {
                operationId = "acceptTrade"
                tag("trade")
            }

            post("/{id}/cancel") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                call.respond(tradeService.cancelTrade(id, call.requireUserId()))
            }.describe {
                operationId = "cancelTrade"
                tag("trade")
            }

            post("/{id}/complete") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                call.respond(tradeService.completeTrade(id, call.requireUserId()))
            }.describe {
                operationId = "completeTrade"
                tag("trade")
            }

            put("/{id}/location-mode") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                val body = call.receive<SetLocationModeRequest>()
                call.respond(tradeService.setLocationMode(id, call.requireUserId(), body))
            }.describe {
                operationId = "setTradeLocationMode"
                tag("trade")
            }

            put("/{id}/fulfillment-place") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                val body = call.receive<SetFulfillmentPlaceRequest>()
                call.respond(tradeService.setFulfillmentPlace(id, call.requireUserId(), body))
            }.describe {
                operationId = "setTradeFulfillmentPlace"
                tag("trade")
            }

            post("/{id}/meetup/propose") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                call.respond(tradeService.proposeMeetup(id, call.requireUserId()))
            }.describe {
                operationId = "proposeMeetup"
                tag("trade")
            }

            post("/{id}/meetup/confirm") {
                val id = call.parameters["id"]?.let(Ids::parseOrNull)
                    ?: badRequest("id required")
                call.respond(tradeService.confirmMeetup(id, call.requireUserId()))
            }.describe {
                operationId = "confirmMeetup"
                tag("trade")
            }
        }
    }
}
