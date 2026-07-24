package com.zula.features.user

import com.zula.features.user.persistence.UserRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.binds
import org.koin.dsl.module

val userModule = module {
    singleOf(::UserRepository)
    singleOf(::UserService) binds arrayOf(UserProfileWriter::class)
}
