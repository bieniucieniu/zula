package com.zula.features.geolocation

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val geolocationModule = module {
    singleOf(::GeolocationService)
    singleOf(::GeolocationPublisher)
    singleOf(::GeolocationConsumer)
}
