package com.zula.features.trade

import org.koin.dsl.module

val tradeModule = module {
    single { TradeService() }
    single { TradePublisher() }
    single { TradeConsumer(get()) }
}
