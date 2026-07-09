package com.zula.features.feed

import org.koin.dsl.module

val feedModule = module {
    single { FeedService() }
    single { FeedPublisher() }
    single { FeedConsumer(get()) }
}
