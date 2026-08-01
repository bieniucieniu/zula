package com.zula.features.chat

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val chatModule = module {
    singleOf(::ChatRepository)
    singleOf(::SseEventHub) bind SseEventSink::class
    singleOf(::ChatService) bind ChatRoomEnsurer::class
    singleOf(::ChatPublisher)
    singleOf(::ChatConsumer)
}
