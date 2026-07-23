package com.zula.features.chat

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val chatModule = module {
    singleOf(::ChatService)
    singleOf(::ChatPublisher)
    singleOf(::ChatConsumer)
}
