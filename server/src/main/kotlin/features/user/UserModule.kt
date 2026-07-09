package com.zula.features.user

import com.zula.core.contracts.BlockResolver
import com.zula.core.contracts.SellerActivityWriter
import com.zula.core.contracts.TrustLedgerWriter
import org.koin.dsl.module

val userModule = module {
    single { UserService() }
    single<BlockResolver> { get<UserService>() }
    single<TrustLedgerWriter> { get<UserService>() }
    single<SellerActivityWriter> { get<UserService>() }
}
