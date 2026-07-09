package com.zula.features.validation

import org.koin.dsl.module

val validationModule = module {
    single { ValidationService() }
    single { ValidationPublisher() }
    single { ValidationConsumer(get()) }
}
