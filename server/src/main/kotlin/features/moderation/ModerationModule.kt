package com.zula.features.moderation

import org.koin.dsl.module

val moderationModule = module {
    single { ModerationService() }
    single { ModerationPublisher() }
    single { ModerationConsumer(get()) }
}
