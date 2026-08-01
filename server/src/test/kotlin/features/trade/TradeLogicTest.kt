package com.zula.features.trade

import com.zula.core.http.HttpException
import com.zula.features.trade.domain.TradeStatuses
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TradeLogicTest {
    @Test
    fun acceptOnlyFromProposed() {
        TradeStateMachine.requireAccept(TradeStatuses.PROPOSED)
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireAccept(TradeStatuses.ACCEPTED)
        }
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireAccept(TradeStatuses.CANCELLED)
        }
    }

    @Test
    fun cancelRejectsTerminal() {
        TradeStateMachine.requireCancel(TradeStatuses.PROPOSED)
        TradeStateMachine.requireCancel(TradeStatuses.ACCEPTED)
        TradeStateMachine.requireCancel(TradeStatuses.SCHEDULED)
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireCancel(TradeStatuses.COMPLETED)
        }
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireCancel(TradeStatuses.CANCELLED)
        }
    }

    @Test
    fun completeOnlyAcceptedOrScheduled() {
        TradeStateMachine.requireComplete(TradeStatuses.ACCEPTED)
        TradeStateMachine.requireComplete(TradeStatuses.SCHEDULED)
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireComplete(TradeStatuses.PROPOSED)
        }
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireComplete(TradeStatuses.COMPLETED)
        }
    }

    @Test
    fun meetupTransitions() {
        TradeStateMachine.requireProposeMeetup(TradeStatuses.ACCEPTED)
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireProposeMeetup(TradeStatuses.PROPOSED)
        }
        TradeStateMachine.requireConfirmMeetup(TradeStatuses.SCHEDULED)
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireConfirmMeetup(TradeStatuses.ACCEPTED)
        }
    }

    @Test
    fun fulfillmentPlaceRequiresClientAfterAccept() {
        TradeStateMachine.requireSetFulfillmentPlace(TradeStatuses.ACCEPTED, "client")
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireSetFulfillmentPlace(TradeStatuses.PROPOSED, "client")
        }
        assertFailsWith<HttpException.Conflict> {
            TradeStateMachine.requireSetFulfillmentPlace(TradeStatuses.ACCEPTED, "provider")
        }
    }
}
