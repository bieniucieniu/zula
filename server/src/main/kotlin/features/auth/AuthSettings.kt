package com.zula.features.auth

data class AuthSettings(
    val providerCheckIntervalSeconds: Long = 21_600,
    val requireProviderRefreshOnLogin: Boolean = true,
    val appleCodeFlowEnabled: Boolean = false,
) {
    companion object {
        fun from(config: io.ktor.server.config.ApplicationConfig?): AuthSettings {
            config ?: return AuthSettings()
            return AuthSettings(
                providerCheckIntervalSeconds = config.propertyOrNull("providerCheckIntervalSeconds")
                    ?.getString()?.toLongOrNull() ?: 21_600,
                requireProviderRefreshOnLogin = config.propertyOrNull("requireProviderRefreshOnLogin")
                    ?.getString()?.toBooleanStrictOrNull() ?: true,
                appleCodeFlowEnabled = config.propertyOrNull("appleCodeFlowEnabled")
                    ?.getString()?.toBooleanStrictOrNull() ?: false,
            )
        }
    }
}
