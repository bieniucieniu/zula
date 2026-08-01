package com.zula.features.groups

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val groupModule = module {
    singleOf(::GroupRepository)
    singleOf(::GroupService)
}
