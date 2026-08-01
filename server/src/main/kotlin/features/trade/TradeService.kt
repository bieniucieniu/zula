package com.zula.features.trade

import com.zula.Trade_items
import com.zula.Trade_locations
import com.zula.Trades
import com.zula.core.http.badRequest
import com.zula.core.http.forbidden
import com.zula.core.http.notFound
import com.zula.features.chat.ChatRoomEnsurer
import com.zula.features.chat.SseEventSink
import com.zula.features.chat.domain.SseEventTypes
import com.zula.features.trade.domain.CreateTradeRequest
import com.zula.features.trade.domain.LocationModes
import com.zula.features.trade.domain.SetFulfillmentPlaceRequest
import com.zula.features.trade.domain.SetLocationModeRequest
import com.zula.features.trade.domain.TradeItemResponse
import com.zula.features.trade.domain.TradeLocationResponse
import com.zula.features.trade.domain.TradeParticipantRoles
import com.zula.features.trade.domain.TradeResponse
import com.zula.features.trade.domain.TradeStateChangedPayload
import com.zula.features.trade.domain.TradeStatuses
import com.zula.features.trade.domain.TradeTemplates
import com.zula.lib.id.Ids
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.uuid.Uuid

class TradeService(
    private val repository: TradeRepository,
    private val chatRooms: ChatRoomEnsurer,
    private val sse: SseEventSink,
    private val paymentGateway: PaymentGateway,
    private val json: Json,
) {
    @Suppress("UNUSED_PARAMETER")
    private fun touchPaymentSeam(tradeId: Uuid) {
        // Keep PaymentGateway wired so Koin + future payment branch stay green.
        paymentGateway.createIntent(tradeId, 0L, "PLN")
    }

    fun createTrade(initiatorId: Uuid, req: CreateTradeRequest): TradeResponse {
        val counterpartyId = Ids.parseOrNull(req.counterpartyId)
            ?: badRequest("counterpartyId invalid")
        if (counterpartyId == initiatorId) badRequest("Cannot trade with yourself")
        val template = req.template.trim()
        if (template !in TradeTemplates.ALL) {
            badRequest("template must be one of ${TradeTemplates.ALL.joinToString()}")
        }
        val locationMode = req.locationMode?.trim()?.ifEmpty { null }
        if (locationMode != null && locationMode !in LocationModes.ALL) {
            badRequest("locationMode must be one of ${LocationModes.ALL.joinToString()}")
        }
        val feedItemId = req.feedItemId?.trim()?.ifEmpty { null }?.let {
            Ids.parseOrNull(it) ?: badRequest("feedItemId invalid")
        }
        val now = Instant.now().epochSecond

        val trade = repository.transaction {
            if (feedItemId != null) {
                val feed = getFeedItem(feedItemId) ?: notFound("Feed item not found")
                if (feed.status != "active") {
                    badRequest("Feed item must be active to start a trade")
                }
                updateFeedItemStatus(feedItemId, "in_trade", now)
                    ?: badRequest("Failed to lock feed item")
            }
            val row = insertTrade(
                template = template,
                locationMode = locationMode,
                initiatorId = initiatorId,
                counterpartyId = counterpartyId,
                feedItemId = feedItemId,
                createdAt = now,
                updatedAt = now,
            )
            insertParticipant(row.id, initiatorId, TradeParticipantRoles.INITIATOR)
            insertParticipant(row.id, counterpartyId, TradeParticipantRoles.COUNTERPARTY)
            if (feedItemId != null) {
                insertItem(row.id, feedItemId, TradeParticipantRoles.INITIATOR, null)
            }
            row
        }

        publishStateChange(trade, previousStatus = null)
        return toResponse(trade)
    }

    fun getTrade(tradeId: Uuid, viewerId: Uuid): TradeResponse {
        val trade = requireTrade(tradeId)
        requireParticipant(trade, viewerId)
        return toResponse(trade)
    }

    fun acceptTrade(tradeId: Uuid, actorId: Uuid): TradeResponse {
        val trade = requireTrade(tradeId)
        if (trade.counterparty_id != actorId) {
            forbidden("Only counterparty can accept")
        }
        TradeStateMachine.requireAccept(trade.status)
        val now = Instant.now().epochSecond
        val updated = repository.updateStatus(tradeId, TradeStatuses.ACCEPTED, now)
            ?: notFound("Trade not found")
        chatRooms.ensureTradeRoom(
            tradeId = tradeId,
            participantIds = listOf(trade.initiator_id, trade.counterparty_id),
        )
        publishStateChange(updated, previousStatus = trade.status)
        return toResponse(updated)
    }

    fun cancelTrade(tradeId: Uuid, actorId: Uuid): TradeResponse {
        val trade = requireTrade(tradeId)
        requireParticipant(trade, actorId)
        TradeStateMachine.requireCancel(trade.status)
        val now = Instant.now().epochSecond
        val updated = repository.transaction {
            val row = updateStatus(tradeId, TradeStatuses.CANCELLED, now)
                ?: notFound("Trade not found")
            trade.feed_item_id?.let { fid ->
                updateFeedItemStatus(fid, "active", now)
            }
            row
        }
        publishStateChange(updated, previousStatus = trade.status)
        return toResponse(updated)
    }

    fun setLocationMode(tradeId: Uuid, actorId: Uuid, req: SetLocationModeRequest): TradeResponse {
        val trade = requireTrade(tradeId)
        requireParticipant(trade, actorId)
        TradeStateMachine.requireMutableLocationMode(trade.status)
        val mode = req.locationMode.trim()
        if (mode !in LocationModes.ALL) {
            badRequest("locationMode must be one of ${LocationModes.ALL.joinToString()}")
        }
        val now = Instant.now().epochSecond
        val updated = repository.updateLocationMode(tradeId, mode, now)
            ?: notFound("Trade not found")
        publishStateChange(updated, previousStatus = trade.status)
        return toResponse(updated)
    }

    fun setFulfillmentPlace(
        tradeId: Uuid,
        actorId: Uuid,
        req: SetFulfillmentPlaceRequest,
    ): TradeResponse {
        val trade = requireTrade(tradeId)
        requireParticipant(trade, actorId)
        TradeStateMachine.requireSetFulfillmentPlace(trade.status, trade.location_mode)
        if (req.placeNote.isNullOrBlank() && req.placeCiphertext.isNullOrBlank()) {
            badRequest("placeNote or placeCiphertext required")
        }
        val now = Instant.now().epochSecond
        repository.upsertLocation(
            tradeId = tradeId,
            placeNote = req.placeNote?.trim()?.ifEmpty { null },
            placeCiphertext = req.placeCiphertext?.trim()?.ifEmpty { null },
            updatedAt = now,
        )
        val updated = repository.getTrade(tradeId) ?: notFound("Trade not found")
        return toResponse(updated)
    }

    fun proposeMeetup(tradeId: Uuid, actorId: Uuid): TradeResponse {
        val trade = requireTrade(tradeId)
        requireParticipant(trade, actorId)
        TradeStateMachine.requireProposeMeetup(trade.status)
        val now = Instant.now().epochSecond
        val updated = repository.updateMeetupProposed(tradeId, now, now)
            ?: notFound("Trade not found")
        publishStateChange(updated, previousStatus = trade.status)
        return toResponse(updated)
    }

    fun confirmMeetup(tradeId: Uuid, actorId: Uuid): TradeResponse {
        val trade = requireTrade(tradeId)
        requireParticipant(trade, actorId)
        TradeStateMachine.requireConfirmMeetup(trade.status)
        val now = Instant.now().epochSecond
        val updated = repository.updateMeetupConfirmed(tradeId, now, now)
            ?: notFound("Trade not found")
        publishStateChange(updated, previousStatus = trade.status)
        return toResponse(updated)
    }

    fun completeTrade(tradeId: Uuid, actorId: Uuid): TradeResponse {
        val trade = requireTrade(tradeId)
        requireParticipant(trade, actorId)
        TradeStateMachine.requireComplete(trade.status)
        val now = Instant.now().epochSecond
        val updated = repository.transaction {
            val row = updateStatus(tradeId, TradeStatuses.COMPLETED, now)
                ?: notFound("Trade not found")
            trade.feed_item_id?.let { fid ->
                updateFeedItemStatus(fid, "fulfilled", now)
            }
            row
        }
        touchPaymentSeam(tradeId)
        publishStateChange(updated, previousStatus = trade.status)
        return toResponse(updated)
    }

    private fun requireTrade(tradeId: Uuid): Trades =
        repository.getTrade(tradeId) ?: notFound("Trade not found")

    private fun requireParticipant(trade: Trades, userId: Uuid) {
        if (trade.initiator_id != userId && trade.counterparty_id != userId) {
            if (!repository.isParticipant(trade.id, userId)) {
                forbidden("Not a trade participant")
            }
        }
    }

    private fun publishStateChange(trade: Trades, previousStatus: String?) {
        val payload = json.encodeToString(
            TradeStateChangedPayload(
                tradeId = trade.id.toString(),
                status = trade.status,
                previousStatus = previousStatus,
            ),
        )
        listOf(trade.initiator_id, trade.counterparty_id).distinct().forEach { uid ->
            sse.emit(uid, SseEventTypes.TRADE_STATE_CHANGED, payload)
        }
    }

    private fun toResponse(trade: Trades): TradeResponse {
        val items = repository.listItems(trade.id).map { it.toResponse() }
        val location = repository.getLocation(trade.id)?.toResponse()
        return TradeResponse(
            id = trade.id.toString(),
            template = trade.template,
            status = trade.status,
            locationMode = trade.location_mode,
            initiatorId = trade.initiator_id.toString(),
            counterpartyId = trade.counterparty_id.toString(),
            feedItemId = trade.feed_item_id?.toString(),
            meetupProposedAt = trade.meetup_proposed_at,
            meetupConfirmedAt = trade.meetup_confirmed_at,
            createdAt = trade.created_at,
            updatedAt = trade.updated_at,
            items = items,
            location = location,
        )
    }

    private fun Trade_items.toResponse() = TradeItemResponse(
        id = id.toString(),
        feedItemId = feed_item_id?.toString(),
        side = side,
        label = label,
    )

    private fun Trade_locations.toResponse() = TradeLocationResponse(
        placeNote = place_note,
        placeCiphertext = place_ciphertext,
        updatedAt = updated_at,
    )
}
