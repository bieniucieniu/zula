package com.zula.features.sync

import org.koin.dsl.module

val syncModule = module {
    single { SyncService(get()) }
}
