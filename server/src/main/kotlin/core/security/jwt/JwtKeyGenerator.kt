package com.zula.core.security.jwt

import com.zula.core.security.SecurityConfig
import com.zula.core.security.jwt.keys.KeysManagers
import com.zula.core.security.jwt.keys.KeysValidation
import com.zula.core.security.jwt.keys.PodNamespace
import io.ktor.server.config.MapApplicationConfig
import org.slf4j.LoggerFactory

/**
 * CLI for JWT key management.
 *
 * ```
 * ./gradlew :server:manageJwtKeys -PjwtKeysCommand=generate
 * ./gradlew :server:manageJwtKeys -PjwtKeysCommand=push -PjwtK8sEnabled=true
 * ./gradlew :server:manageJwtKeys -PjwtKeysCommand=pull -PjwtK8sEnabled=true
 * ./gradlew :server:manageJwtKeys -PjwtKeysCommand=validate
 * ```
 */
fun main(args: Array<String>) {
    val command = System.getProperty("jwtKeysCommand") ?: args.firstOrNull() ?: "generate"
    val namespace = System.getProperty("jwtSecretNamespace") ?: PodNamespace.current()
    val secretName = System.getProperty("jwtSecretName") ?: "zula-jwt-keys"
    val k8sEnabled = System.getProperty("jwtK8sEnabled")?.toBooleanStrictOrNull() ?: false

    val config = SecurityConfig.from(
        MapApplicationConfig(
            "security.jwt.audience" to "zula",
            "security.jwt.realm" to "Zula",
            "security.jwt.autoGenerateKey" to "true",
            "security.jwt.kubernetes.enabled" to k8sEnabled.toString(),
            "security.jwt.kubernetes.namespace" to namespace,
            "security.jwt.kubernetes.secretName" to secretName,
            "security.jwt.kubernetes.autoPull" to "true",
            "security.jwt.kubernetes.autoPush" to "false",
        ),
    )

    val manager = KeysManagers.create(config.jwt, LoggerFactory.getLogger("JwtKeyGenerator"))

    when (command) {
        "generate" -> {
            val keys = manager.generate()
            manager.validate(keys).printResult()
            print(keys.toKubernetesSecretYaml(namespace, secretName))
        }
        "validate" -> {
            val keys = manager.resolve()
            manager.validate(keys).printResult()
        }
        "pull" -> {
            val keys = manager.pullFromKubernetes()
                ?: error("Secret $namespace/$secretName not found or Kubernetes integration disabled")
            manager.validate(keys).printResult()
            print(keys.toKubernetesSecretYaml(namespace, secretName))
        }
        "push" -> {
            val keys = manager.generate()
            manager.pushToKubernetes(keys)
            manager.validate(keys).printResult()
            print(keys.toKubernetesSecretYaml(namespace, secretName))
        }
        else -> error("Unknown command '$command'. Use: generate, validate, pull, push")
    }
}

private fun KeysValidation.printResult() {
    when (this) {
        KeysValidation.Valid -> println("JWT keys: valid")
        is KeysValidation.Invalid -> error("JWT keys: invalid — ${reasons.joinToString("; ")}")
    }
}
