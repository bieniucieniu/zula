package com.zula.features.trade

import org.koin.dsl.module

val tradeModule = module {
    single { _root_ide_package_.com.zula.features.trade.TradeService() }
    single { _root_ide_package_.com.zula.features.trade.TradePublisher() }
    single { _root_ide_package_.com.zula.features.trade.TradeConsumer(get()) }
}
