package com.zula.features.user

/**
 * Admin allowlist by Google OAuth subject (`provider_user_id`).
 * Empty set = no admins.
 */
data class UserAdminConfig(
    val googleSubjectIds: Set<String> = emptySet(),
) {
    fun isAdminGoogleSubject(providerUserId: String?): Boolean {
        if (providerUserId.isNullOrBlank() || googleSubjectIds.isEmpty()) return false
        return providerUserId in googleSubjectIds
    }

    companion object {
        fun fromEnv(raw: String?): UserAdminConfig {
            val ids = raw
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
            return UserAdminConfig(googleSubjectIds = ids)
        }
    }
}
