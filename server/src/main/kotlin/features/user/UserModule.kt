package com.zula.features.user

import com.zula.core.contracts.BlockResolver
import com.zula.core.contracts.SellerActivityWriter
import com.zula.core.contracts.TrustLedgerWriter
import com.zula.features.user.persistence.UserRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module

val userModule = module {
    singleOf(::UserRepository)
    singleOf(::UserService) binds arrayOf(
        UserProfileWriter::class,
        BlockResolver::class,
        TrustLedgerWriter::class,
        SellerActivityWriter::class,
    )
}
