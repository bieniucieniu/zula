package com.zula.core.security.jwt

/**
 * Generates an RSA JWT key pair and prints a Kubernetes Secret manifest.
 *
 * Usage:
 * ```
 * ./gradlew :server:generateJwtK8sSecret -PjwtSecretNamespace=zula -PjwtSecretName=zula-jwt-keys
 * ```
 */
fun main(args: Array<String>) {
    val namespace = System.getProperty("jwtSecretNamespace")
        ?: args.getOrNull(0)
        ?: "zula"
    val secretName = System.getProperty("jwtSecretName")
        ?: args.getOrNull(1)
        ?: "zula-jwt-keys"

    val keys = JwtKeys.generateRsa2048()
    print(keys.toKubernetesSecretYaml(namespace, secretName))
}
