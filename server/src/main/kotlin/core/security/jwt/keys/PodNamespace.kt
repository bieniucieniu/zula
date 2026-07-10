package com.zula.core.security.jwt.keys

import java.nio.file.Path

object PodNamespace {
    private const val SERVICE_ACCOUNT_NAMESPACE_FILE =
        "/var/run/secrets/kubernetes.io/serviceaccount/namespace"

    fun current(): String =
        System.getenv("POD_NAMESPACE")?.takeIf { it.isNotBlank() }
            ?: System.getenv("KUBERNETES_NAMESPACE")?.takeIf { it.isNotBlank() }
            ?: readNamespaceFile()
            ?: "default"

    private fun readNamespaceFile(): String? =
        runCatching {
            Path.of(SERVICE_ACCOUNT_NAMESPACE_FILE).toFile().readText().trim()
        }.getOrNull()?.takeIf { it.isNotBlank() }
}
