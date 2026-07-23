package com.zula.features.moderation

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val moderationModule = module {
    singleOf(::ModerationService)
    singleOf(::ModerationPublisher)
    singleOf(::ModerationConsumer)

}
