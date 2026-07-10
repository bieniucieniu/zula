package com.zula.features.feed

import org.koin.dsl.module

val feedModule = module {
    single { _root_ide_package_.com.zula.features.feed.FeedService() }
    single { _root_ide_package_.com.zula.features.feed.FeedPublisher() }
    single { _root_ide_package_.com.zula.features.feed.FeedConsumer(get()) }
}
