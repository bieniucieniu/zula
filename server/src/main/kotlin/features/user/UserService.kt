package com.zula.features.user

import com.zula.core.contracts.BlockResolver
import com.zula.core.contracts.SellerActivityWriter
import com.zula.core.contracts.TrustLedgerWriter

class UserService :
    BlockResolver,
    TrustLedgerWriter,
    SellerActivityWriter {
    override suspend fun resolveViewerBlock(viewerId: String?, targetUserId: String): Boolean = false

    override suspend fun applyTrustEvent(userId: String, eventType: String, delta: Int) {
        // TODO: ledger + cache
    }

    override suspend fun syncSellerActivityStats(sellerId: String) {
        // TODO: write-through projection
    }
}
