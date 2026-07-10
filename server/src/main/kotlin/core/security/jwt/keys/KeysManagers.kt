package com.zula.core.security.jwt.keys

import com.zula.core.security.JwtConfig
import com.zula.core.security.jwt.JwtKeys
import org.slf4j.Logger

object KeysManagers {
    fun create(config: JwtConfig, log: Logger): KeysManager {
        val store = if (config.kubernetes.enabled) {
            KubernetesJwtKeysStore()
        } else {
            NoopJwtKeysStore
        }
        return DefaultKeysManager(config, store, log)
    }
}

private object NoopJwtKeysStore : JwtKeysStore {
    override fun pull(target: JwtKeysTarget): JwtKeys? = null

    override fun push(target: JwtKeysTarget, keys: JwtKeys) {
        error("Kubernetes JWT store is disabled (security.jwt.kubernetes.enabled=false)")
    }
}
