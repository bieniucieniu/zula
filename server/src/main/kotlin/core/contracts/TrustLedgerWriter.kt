package com.zula.core.contracts

interface TrustLedgerWriter {
    suspend fun applyTrustEvent(userId: String, eventType: String, delta: Int)
}
