package com.zula.features.auth

import org.koin.dsl.module

val authModule = module {
    single { _root_ide_package_.com.zula.features.auth.AuthService() }
}
