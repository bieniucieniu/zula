package com.zula.features.geolocation

import org.koin.dsl.module

val geolocationModule = module {
    single { GeolocationService() }
    single { GeolocationPublisher() }
    single { GeolocationConsumer(get()) }
}
