package com.zula.features.chat

import org.koin.dsl.module

val chatModule = module {
    single { ChatService() }
    single { ChatPublisher() }
    single { ChatConsumer(get()) }
}
