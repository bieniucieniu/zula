package com.zula.core.contracts

interface SellerActivityWriter {
    suspend fun syncSellerActivityStats(sellerId: String)
}
