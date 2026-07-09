package com.zula.features.auth

import org.koin.dsl.module

val authModule = module {
    single { AuthService() }
}
