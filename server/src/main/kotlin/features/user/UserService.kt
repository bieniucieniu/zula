package com.zula.features.user

import com.zula.core.contracts.BlockResolver
import com.zula.core.contracts.SellerActivityWriter
import com.zula.core.contracts.TrustLedgerWriter
import com.zula.core.http.badRequest
import com.zula.core.http.forbidden
import com.zula.features.user.persistence.UserRepository
import kotlin.uuid.Uuid

/**
 * Domain writes for user/profile. PowerSync upload registry calls these —
 * do not apply raw CRUD SQL from the sync endpoint.
 */
class UserService(
    private val repository: UserRepository,
) : UserProfileWriter,
    BlockResolver,
    TrustLedgerWriter,
    SellerActivityWriter {
    override suspend fun resolveViewerBlock(viewerId: String?, targetUserId: String): Boolean = false

    override suspend fun applyTrustEvent(userId: String, eventType: String, delta: Int) {
        // TODO: ledger + cache
    }

    override suspend fun syncSellerActivityStats(sellerId: String) {
        // TODO: write-through projection
    }

    /**
     * Full replace of editable profile fields (PowerSync PUT).
     * [profileId] must equal [actorId] (own profile only).
     */
    override fun putMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) {
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

    /**
     * Partial update (PowerSync PATCH). Null fields in [write] mean "leave unchanged".
     */
    override fun patchMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) {
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
