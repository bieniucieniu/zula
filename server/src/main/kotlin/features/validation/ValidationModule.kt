package com.zula.features.validation

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val validationModule = module {
    singleOf(::ValidationService)
    singleOf(::ValidationPublisher)
    singleOf(::ValidationConsumer)
}
