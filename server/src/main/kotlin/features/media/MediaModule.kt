package com.zula.features.media

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mediaModule = module {
    singleOf(::MediaRepository)
    singleOf(::MediaService)
    singleOf(::MediaGcJobs)
    singleOf(::MediaPublisher)
    singleOf(::MediaConsumer)
}
