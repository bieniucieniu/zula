package com.zula.features.moderation

import org.koin.dsl.module

val moderationModule = module {
    single { _root_ide_package_.com.zula.features.moderation.ModerationService() }
    single { _root_ide_package_.com.zula.features.moderation.ModerationPublisher() }
    single { _root_ide_package_.com.zula.features.moderation.ModerationConsumer(get()) }
}
