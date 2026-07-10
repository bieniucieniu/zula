package com.zula.features.media

import org.koin.dsl.module

val mediaModule = module {
    single { _root_ide_package_.com.zula.features.media.MediaService() }
    single { _root_ide_package_.com.zula.features.media.MediaPublisher() }
    single { _root_ide_package_.com.zula.features.media.MediaConsumer(get()) }
}
