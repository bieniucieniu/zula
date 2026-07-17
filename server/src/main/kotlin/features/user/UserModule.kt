package com.zula.features.user

import com.zula.core.contracts.BlockResolver
import com.zula.core.contracts.SellerActivityWriter
import com.zula.core.contracts.TrustLedgerWriter
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.bind

val userModule = module {
    single { UserService() }
        .bind(BlockResolver::class)
        .bind(TrustLedgerWriter::class)
        .bind(SellerActivityWriter::class)
}
