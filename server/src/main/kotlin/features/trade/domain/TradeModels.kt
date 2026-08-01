package com.zula.features.trade.domain

import kotlinx.serialization.Serializable

object TradeTemplates {
    const val SWAP = "swap"
    const val MEETUP_CASH = "meetup_cash"
    val ALL = setOf(SWAP, MEETUP_CASH)
}

object TradeStatuses {
    const val PROPOSED = "proposed"
    const val ACCEPTED = "accepted"
    const val SCHEDULED = "scheduled"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"
    const val EXPIRED = "expired"
}

object LocationModes {
    const val PROVIDER = "provider"
    const val CLIENT = "client"
    const val NEGOTIATED = "negotiated"
    val ALL = setOf(PROVIDER, CLIENT, NEGOTIATED)
}

object TradeParticipantRoles {
    const val INITIATOR = "initiator"
    const val COUNTERPARTY = "counterparty"
}

@Serializable
data class CreateTradeRequest(
    val counterpartyId: String,
    val template: String,
    val feedItemId: String? = null,
    val locationMode: String? = null,
)

@Serializable
data class SetLocationModeRequest(
    val locationMode: String,
)

@Serializable
data class SetFulfillmentPlaceRequest(
    val placeNote: String? = null,
    val placeCiphertext: String? = null,
)

@Serializable
data class TradeItemResponse(
    val id: String,
    val feedItemId: String? = null,
    val side: String,
    val label: String? = null,
)

@Serializable
data class TradeLocationResponse(
    val placeNote: String? = null,
    val placeCiphertext: String? = null,
    val updatedAt: Long,
)

@Serializable
data class TradeResponse(
    val id: String,
    val template: String,
    val status: String,
    val locationMode: String? = null,
    val initiatorId: String,
    val counterpartyId: String,
    val feedItemId: String? = null,
    val meetupProposedAt: Long? = null,
    val meetupConfirmedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<TradeItemResponse> = emptyList(),
    val location: TradeLocationResponse? = null,
)

@Serializable
data class TradeStateChangedPayload(
    val tradeId: String,
    val status: String,
    val previousStatus: String? = null,
)
