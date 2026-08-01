package com.zula.features.trade

import kotlin.uuid.Uuid

/**
 * Payment-ready seam. MVP binding is [NoopPaymentGateway] — no external rails.
 * Reserved SSE names: PAYMENT_SUCCESS / PAYMENT_FAILED (see chat domain).
 */
interface PaymentGateway {
    fun createIntent(tradeId: Uuid, amountMinor: Long, currency: String): String?
}

class NoopPaymentGateway : PaymentGateway {
    override fun createIntent(tradeId: Uuid, amountMinor: Long, currency: String): String? = null
}
