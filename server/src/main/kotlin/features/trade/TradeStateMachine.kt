package com.zula.features.trade

import com.zula.features.trade.domain.TradeStatuses
import com.zula.core.http.conflict

/** Pure transition guards for unit tests and TradeService. */
object TradeStateMachine {
    fun requireAccept(status: String) {
        if (status != TradeStatuses.PROPOSED) {
            conflict("Trade cannot be accepted from status=$status")
        }
    }

    fun requireCancel(status: String) {
        if (status !in setOf(TradeStatuses.PROPOSED, TradeStatuses.ACCEPTED, TradeStatuses.SCHEDULED)) {
            conflict("Trade cannot be cancelled from status=$status")
        }
    }

    fun requireProposeMeetup(status: String) {
        if (status != TradeStatuses.ACCEPTED) {
            conflict("Meetup can only be proposed when accepted (status=$status)")
        }
    }

    fun requireConfirmMeetup(status: String) {
        if (status != TradeStatuses.SCHEDULED) {
            conflict("Meetup can only be confirmed when scheduled (status=$status)")
        }
    }

    fun requireComplete(status: String) {
        if (status !in setOf(TradeStatuses.ACCEPTED, TradeStatuses.SCHEDULED)) {
            conflict("Trade cannot be completed from status=$status")
        }
    }

    fun requireMutableLocationMode(status: String) {
        if (status in setOf(TradeStatuses.COMPLETED, TradeStatuses.CANCELLED, TradeStatuses.EXPIRED)) {
            conflict("Location mode cannot change when status=$status")
        }
    }

    fun requireSetFulfillmentPlace(status: String, locationMode: String?) {
        if (status !in setOf(TradeStatuses.ACCEPTED, TradeStatuses.SCHEDULED)) {
            conflict("Fulfillment place only after accept (status=$status)")
        }
        if (locationMode != "client") {
            conflict("Fulfillment place requires location_mode=client")
        }
    }
}
