package com.zula.features.chat

import org.koin.dsl.module

val chatModule = module {
    single { _root_ide_package_.com.zula.features.chat.ChatService() }
    single { _root_ide_package_.com.zula.features.chat.ChatPublisher() }
    single { _root_ide_package_.com.zula.features.chat.ChatConsumer(get()) }
}
