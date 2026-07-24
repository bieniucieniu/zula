package com.zula.features.user

import com.zula.core.http.badRequest
import com.zula.core.http.forbidden
import com.zula.features.user.persistence.UserRepository
import kotlin.uuid.Uuid

/**
 * Domain writes for user/profile. REST handlers call these — do not apply raw CRUD SQL.
 */
class UserService(
    private val repository: UserRepository,
) : UserProfileWriter {
    /**
     * Partial update of editable profile fields.
     * Null fields in [write] mean "leave unchanged".
     * [profileId] must equal [actorId] (own profile only).
     */
    override fun updateMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) {
        requireOwnProfile(actorId, profileId)
        val now = System.currentTimeMillis() / 1000
        val existing = repository.getProfile(profileId)
            ?: badRequest("Profile not found")
        repository.updateProfile(
            userId = profileId,
            displayName = write.displayName ?: existing.display_name,
            avatarUrl = write.avatarUrl ?: existing.avatar_url,
            bio = write.bio ?: existing.bio,
            timezone = write.timezone ?: existing.timezone,
            preferredLanguage = write.preferredLanguage ?: existing.preferred_language,
            locationTag = write.locationTag ?: existing.location_tag,
            sellerHeadline = write.sellerHeadline ?: existing.seller_headline,
            updatedAt = now,
        )
    }

    private fun requireOwnProfile(actorId: Uuid, profileId: Uuid) {
        if (actorId != profileId) {
            forbidden("Can only modify own profile")
        }
    }
}
