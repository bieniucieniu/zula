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

/** Own-profile writes used by REST and PowerSync upload registry. */
interface UserProfileWriter {
    fun putMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite)

    fun patchMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite)
}
