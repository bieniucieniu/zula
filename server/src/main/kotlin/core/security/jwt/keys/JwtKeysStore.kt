package com.zula.core.security.jwt.keys

import com.zula.core.security.jwt.JwtKeys

data class JwtKeysTarget(
    val namespace: String,
    val secretName: String,
)

interface JwtKeysStore {
    fun pull(target: JwtKeysTarget): JwtKeys?

    fun push(target: JwtKeysTarget, keys: JwtKeys)
}
