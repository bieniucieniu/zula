package com.zula.features.user

import kotlin.uuid.Uuid

data class ProfileWrite(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val timezone: String? = null,
    val preferredLanguage: String? = null,
    val locationTag: String? = null,
    val sellerHeadline: String? = null,
)

/** Own-profile writes used by REST handlers. Null fields mean leave unchanged. */
interface UserProfileWriter {
    fun updateMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite)
}
