package com.zula.features.trade

import com.zula.Database
import com.zula.Feed_items
import com.zula.Trade_items
import com.zula.Trade_locations
import com.zula.Trades
import kotlin.uuid.Uuid

class TradeRepository(
    private val database: Database,
) {
    private val trade inline get() = database.tradeQueries
    private val feed inline get() = database.feedQueries

    fun <T> transaction(block: TradeRepository.() -> T): T =
        database.transactionWithResult { this@TradeRepository.block() }

    fun insertTrade(
        template: String,
        locationMode: String?,
        initiatorId: Uuid,
        counterpartyId: Uuid,
        feedItemId: Uuid?,
        createdAt: Long,
        updatedAt: Long,
    ): Trades =
        trade.insertTrade(
            template,
            locationMode,
            initiatorId,
            counterpartyId,
            feedItemId,
            createdAt,
            updatedAt,
        ).executeAsOne()

    fun getTrade(tradeId: Uuid): Trades? =
        trade.getTrade(tradeId).executeAsOneOrNull()

    fun updateStatus(tradeId: Uuid, status: String, updatedAt: Long): Trades? =
        trade.updateTradeStatus(status, updatedAt, tradeId).executeAsOneOrNull()

    fun updateLocationMode(tradeId: Uuid, locationMode: String?, updatedAt: Long): Trades? =
        trade.updateTradeLocationMode(locationMode, updatedAt, tradeId).executeAsOneOrNull()

    fun updateMeetupProposed(tradeId: Uuid, proposedAt: Long, updatedAt: Long): Trades? =
        trade.updateTradeMeetupProposed(proposedAt, updatedAt, tradeId).executeAsOneOrNull()

    fun updateMeetupConfirmed(tradeId: Uuid, confirmedAt: Long, updatedAt: Long): Trades? =
        trade.updateTradeMeetupConfirmed(confirmedAt, updatedAt, tradeId).executeAsOneOrNull()

    fun insertParticipant(tradeId: Uuid, userId: Uuid, role: String) {
        trade.insertTradeParticipant(tradeId, userId, role)
    }

    fun insertItem(
        tradeId: Uuid,
        feedItemId: Uuid?,
        side: String,
        label: String?,
    ): Trade_items =
        trade.insertTradeItem(tradeId, feedItemId, side, label).executeAsOne()

    fun upsertLocation(
        tradeId: Uuid,
        placeNote: String?,
        placeCiphertext: String?,
        updatedAt: Long,
    ) {
        trade.upsertTradeLocation(tradeId, placeNote, placeCiphertext, updatedAt)
    }

    fun getLocation(tradeId: Uuid): Trade_locations? =
        trade.getTradeLocation(tradeId).executeAsOneOrNull()

    fun listItems(tradeId: Uuid): List<Trade_items> =
        trade.listTradeItems(tradeId).executeAsList()

    fun isParticipant(tradeId: Uuid, userId: Uuid): Boolean =
        trade.isTradeParticipant(tradeId, userId).executeAsOne() > 0

    fun getFeedItem(feedItemId: Uuid): Feed_items? =
        feed.getFeedItem(feedItemId).executeAsOneOrNull()

    fun updateFeedItemStatus(feedItemId: Uuid, status: String, updatedAt: Long): Feed_items? =
        feed.updateFeedItemStatus(status, updatedAt, feedItemId).executeAsOneOrNull()
}
