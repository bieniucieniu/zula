package com.zula.features.user.persistence

import com.zula.Database
import com.zula.User_profiles
import kotlin.uuid.Uuid

class UserRepository(
    private val database: Database,
) {
    private val queries inline get() = database.userQueries

    fun getProfile(userId: Uuid): User_profiles? =
        queries.getUserProfile(userId).executeAsOneOrNull()

    fun updateProfile(
        userId: Uuid,
        displayName: String?,
        avatarUrl: String?,
        bio: String?,
        timezone: String?,
        preferredLanguage: String?,
        locationTag: String?,
        sellerHeadline: String?,
        updatedAt: Long,
    ) {
        queries.updateUserProfile(
            display_name = displayName,
            avatar_url = avatarUrl,
            bio = bio,
            timezone = timezone,
            preferred_language = preferredLanguage,
            location_tag = locationTag,
            seller_headline = sellerHeadline,
            updated_at = updatedAt,
            user_id = userId,
        )
    }
}
