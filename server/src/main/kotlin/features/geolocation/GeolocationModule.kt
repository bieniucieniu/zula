package com.zula.features.geolocation

import org.koin.dsl.module

val geolocationModule = module {
    single { _root_ide_package_.com.zula.features.geolocation.GeolocationService() }
    single { _root_ide_package_.com.zula.features.geolocation.GeolocationPublisher() }
    single { _root_ide_package_.com.zula.features.geolocation.GeolocationConsumer(get()) }
}
