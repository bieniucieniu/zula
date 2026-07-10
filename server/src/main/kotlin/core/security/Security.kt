package com.zula.core.security

import com.zula.core.security.jwt.JwtKeyPair
import com.zula.core.security.jwt.SessionJwtIssuer
import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import org.koin.core.module.Module
import org.koin.dsl.module

fun Application.configureSecurity() {
    val bootstrap = SecurityBootstrap.load(this)
    bootstrap.installAuthentication(this)
    attributes.put(SecurityBootstrapKey, bootstrap)
}

fun securityModule(application: Application): Module = module {
    single { application.attributes[SecurityBootstrapKey] }
    single { get<SecurityBootstrap>().config }
    single { get<SecurityBootstrap>().jwtKeys }
    single<JwtKeyPair> { get<SecurityBootstrap>().jwtKeys }
    single<SessionJwtIssuer> { get<SecurityBootstrap>().sessionJwtIssuer }
}
