package com.zula.features.trade

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val tradeModule = module {
    singleOf(::TradeRepository)
    single<PaymentGateway> { NoopPaymentGateway() }
    singleOf(::TradeService)
    singleOf(::TradePublisher)
    singleOf(::TradeConsumer)
}
