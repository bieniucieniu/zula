package com.zula.core.serialization

import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun serializationModule(
    isDev: Boolean = false
) = module {
    single {
        Json {
            encodeDefaults = true
            isLenient = true
            ignoreUnknownKeys = true
            prettyPrint = isDev
            allowComments = true
            allowTrailingComma = true
        }
    }
}
