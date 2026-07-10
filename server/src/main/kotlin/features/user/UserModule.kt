package com.zula.features.user

import com.zula.core.contracts.BlockResolver
import com.zula.core.contracts.SellerActivityWriter
import com.zula.core.contracts.TrustLedgerWriter
import org.koin.dsl.module

val userModule = module {
    single { _root_ide_package_.com.zula.features.user.UserService() }
    single<BlockResolver> { get<com.zula.features.user.UserService>() }
    single<TrustLedgerWriter> { get<com.zula.features.user.UserService>() }
    single<SellerActivityWriter> { get<com.zula.features.user.UserService>() }
}
