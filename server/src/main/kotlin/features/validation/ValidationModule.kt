package com.zula.features.validation

import org.koin.dsl.module

val validationModule = module {
    single { _root_ide_package_.com.zula.features.validation.ValidationService() }
    single { _root_ide_package_.com.zula.features.validation.ValidationPublisher() }
    single { _root_ide_package_.com.zula.features.validation.ValidationConsumer(get()) }
}
