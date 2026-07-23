package com.zula.features.media

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mediaModule = module {
    singleOf(::MediaService)
    singleOf(::MediaPublisher)
    singleOf(::MediaConsumer)
}
