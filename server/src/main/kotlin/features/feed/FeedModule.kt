package com.zula.features.feed

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val feedModule = module {
    singleOf(::FeedService)
    singleOf(::FeedPublisher)
    singleOf(::FeedConsumer)
}
