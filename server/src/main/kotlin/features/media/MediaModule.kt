package com.zula.features.media

import org.koin.dsl.module

val mediaModule = module {
    single { MediaService() }
    single { MediaPublisher() }
    single { MediaConsumer(get()) }
}
