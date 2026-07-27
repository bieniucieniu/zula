package com.zula.features.user

import com.zula.GetSellerProfileById
import com.zula.core.http.badRequest
import com.zula.core.http.forbidden
import com.zula.core.http.notFound
import com.zula.features.user.domain.BlockUserResponse
import com.zula.features.user.domain.ListSellerReviewsResponse
import com.zula.features.user.domain.MyProfileResponse
import com.zula.features.user.domain.ProfileCursor
import com.zula.features.user.domain.SellerActivityCounts
import com.zula.features.user.domain.SellerProfileResponse
import com.zula.features.user.domain.SellerReviewPreview
import com.zula.features.user.domain.UnblockUserResponse
import com.zula.features.user.domain.UpdateMyProfileRequest
import com.zula.features.user.domain.UpdateTrustRequest
import com.zula.features.user.domain.UserResponse
import com.zula.features.user.persistence.UserRepository
import com.zula.lib.id.Ids
import java.time.Instant
import kotlin.uuid.Uuid

/**
 * Domain writes/reads for user/seller profile. REST handlers call these — do not apply raw CRUD SQL.
 */
class UserService(
    private val repository: UserRepository,
    private val adminConfig: UserAdminConfig = UserAdminConfig(),
) : UserProfileWriter {
    fun getSellerProfile(idOrUsername: String, viewerId: Uuid?): SellerProfileResponse {
        val row = resolveSellerRow(idOrUsername)
        enforcePublicTargetAccess(viewerId, row.id)
        val (explicit, implicit) = resolveTrustScores(
            row.id,
            row.explicit_rating_avg,
            row.implicit_trust_score,
            row.last_calculated_at,
        )
        val ratingCount = repository.countUserRatings(row.id).toInt()
        val activity = repository.getSellerActivityStats(row.id)?.let {
            SellerActivityCounts(
                activeOffers = it.active_offer_count.toInt(),
                activeNeeds = it.active_need_count.toInt(),
                activeTrips = it.active_trip_count.toInt(),
                fulfilledItems = it.fulfilled_item_count.toInt(),
                completedTrades = it.completed_trade_count.toInt(),
            )
        }
        val previews = repository.listRecentSellerReviews(row.id, 5).map { review ->
            SellerReviewPreview(
                id = review.id.toString(),
                reviewerUsername = review.reviewer_username,
                rating = review.rating.toInt(),
                comment = review.comment,
                createdAt = Ids.createdAtInstant(review.id).toString(),
            )
        }
        val base = SellerProfileResponse(
            userId = row.id.toString(),
            username = row.username,
            displayName = row.display_name,
            avatarUrl = row.avatar_url,
            bio = row.bio,
            sellerHeadline = row.seller_headline,
            locationTag = row.location_tag,
            memberSince = Ids.createdAtInstant(row.id).toString(),
            explicitRatingAvg = explicit,
            implicitTrustScore = implicit,
            ratingCount = ratingCount,
            activity = activity,
            recentReviews = previews,
        )
        if (viewerId == null) return base
        return base.copy(
            viewerHasBlocked = repository.hasViewerBlockedSeller(viewerId, row.id),
            viewerIsBlocked = repository.hasSellerBlockedViewer(row.id, viewerId),
        )
    }

    fun getMyProfile(ownerId: Uuid): MyProfileResponse {
        val public = getSellerProfile(ownerId.toString(), viewerId = ownerId)
        val profile = repository.getProfile(ownerId)
        return MyProfileResponse(
            public = public,
            timezone = profile?.timezone,
            preferredLanguage = profile?.preferred_language,
            email = repository.primaryEmailForUser(ownerId),
            isAdmin = isAdmin(ownerId),
        )
    }

    fun updateMyProfile(ownerId: Uuid, req: UpdateMyProfileRequest): MyProfileResponse {
        ProfileValidation.validate(req)
        val now = Instant.now().epochSecond
        val existingUser = repository.getUserById(ownerId) ?: notFound("User not found")
        repository.ensureUserProfile(ownerId, existingUser.username, now)
        repository.updateProfile(
            userId = ownerId,
            displayName = req.displayName?.trim(),
            avatarUrl = req.avatarUrl?.trim(),
            bio = req.bio,
            timezone = req.timezone?.trim(),
            preferredLanguage = req.preferredLanguage?.trim()?.lowercase(),
            locationTag = req.locationTag?.trim(),
            sellerHeadline = req.sellerHeadline?.trim(),
            updatedAt = now,
        )
        return getMyProfile(ownerId)
    }

    /**
     * Partial update of editable profile fields.
     * Null fields in [write] mean "leave unchanged".
     * [profileId] must equal [actorId] (own profile only).
     */
    override fun updateMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) {
        requireOwnProfile(actorId, profileId)
        updateMyProfile(
            ownerId = profileId,
            req = UpdateMyProfileRequest(
                displayName = write.displayName,
                bio = write.bio,
                avatarUrl = write.avatarUrl,
                sellerHeadline = write.sellerHeadline,
                locationTag = write.locationTag,
                timezone = write.timezone,
                preferredLanguage = write.preferredLanguage,
            ),
        )
    }

    fun getLegacyUserProfile(idOrUsername: String, viewerId: Uuid?): UserResponse {
        val seller = getSellerProfile(idOrUsername, viewerId)
        return UserResponse(
            userId = seller.userId,
            username = seller.username,
            explicitRatingAvg = seller.explicitRatingAvg,
            implicitTrustScore = seller.implicitTrustScore,
        )
    }

    fun listSellerReviews(
        sellerIdOrUsername: String,
        viewerId: Uuid?,
        cursorId: Uuid?,
        limit: Int,
    ): ListSellerReviewsResponse {
        val row = resolveSellerRow(sellerIdOrUsername)
        enforcePublicTargetAccess(viewerId, row.id)
        val pageSize = limit.coerceIn(1, 50)
        val fetch = pageSize + 1L
        val rows = if (cursorId == null) {
            repository.listSellerReviewsFirstPage(row.id, fetch).map {
                ReviewRow(it.id, it.reviewer_username, it.rating, it.comment)
            }
        } else {
            repository.listSellerReviewsPage(row.id, cursorId, fetch).map {
                ReviewRow(it.id, it.reviewer_username, it.rating, it.comment)
            }
        }
        val hasMore = rows.size > pageSize
        val page = if (hasMore) rows.take(pageSize) else rows
        val reviews = page.map { review ->
            SellerReviewPreview(
                id = review.id.toString(),
                reviewerUsername = review.reviewerUsername,
                rating = review.rating.toInt(),
                comment = review.comment,
                createdAt = Ids.createdAtInstant(review.id).toString(),
            )
        }
        return ListSellerReviewsResponse(
            reviews = reviews,
            nextCursor = reviews.lastOrNull()?.takeIf { hasMore }?.let { ProfileCursor(it.id) },
            hasMore = hasMore,
        )
    }

    fun blockUser(viewerId: Uuid, targetUserId: Uuid): BlockUserResponse {
        if (viewerId == targetUserId) badRequest("Cannot block yourself")
        repository.getUserById(targetUserId) ?: notFound("User not found")
        repository.blockUser(viewerId, targetUserId)
        return BlockUserResponse(blockedUserId = targetUserId.toString())
    }

    fun unblockUser(viewerId: Uuid, targetUserId: Uuid): UnblockUserResponse {
        repository.unblockUser(viewerId, targetUserId)
        return UnblockUserResponse(unblockedUserId = targetUserId.toString())
    }

    fun updateImplicitTrust(adminId: Uuid, targetUserId: Uuid, req: UpdateTrustRequest): UserResponse {
        if (!isAdmin(adminId)) forbidden("Admin only")
        if (req.delta == 0) badRequest("delta must be non-zero")
        repository.getUserById(targetUserId) ?: notFound("User not found")
        val now = Instant.now().epochSecond
        val stats = repository.getUserStats(targetUserId)
        val current = stats?.implicit_trust_score ?: 100L
        val next = (current + req.delta).coerceIn(0, 10_000)
        if (stats == null) {
            repository.upsertUserStatsValues(targetUserId, 5.0, next, now)
        } else {
            repository.updateImplicitTrustScore(targetUserId, next, now)
        }
        return getLegacyUserProfile(targetUserId.toString(), viewerId = null)
    }

    fun recordPeerRating(reviewerId: Uuid, revieweeId: Uuid, rating: Int, comment: String?): Uuid {
        if (reviewerId == revieweeId) badRequest("Cannot rate yourself")
        if (rating !in 1..5) badRequest("rating must be 1–5")
        repository.getUserById(revieweeId) ?: notFound("User not found")
        val id = repository.insertUserRating(reviewerId, revieweeId, rating.toLong(), comment)
        // Invalidate stats cache so next profile read refreshes avg.
        val now = Instant.now().epochSecond
        val stats = repository.getUserStats(revieweeId)
        repository.upsertUserStatsValues(
            userId = revieweeId,
            explicitRatingAvg = stats?.explicit_rating_avg ?: 5.0,
            implicitTrustScore = stats?.implicit_trust_score ?: 100L,
            lastCalculatedAt = now - TRUST_STALE_SECONDS - 1,
        )
        return id
    }

    fun isAdmin(userId: Uuid): Boolean =
        adminConfig.isAdminGoogleSubject(repository.findGoogleProviderUserId(userId))

    private fun enforcePublicTargetAccess(viewerId: Uuid?, targetId: Uuid) {
        if (viewerId == null || viewerId == targetId) return
        if (repository.isBlockedEitherDirection(viewerId, targetId)) {
            notFound("User not found")
        }
    }

    private data class ReviewRow(
        val id: Uuid,
        val reviewerUsername: String,
        val rating: Long,
        val comment: String?,
    )

    private data class SellerRow(
        val id: Uuid,
        val username: String,
        val display_name: String?,
        val avatar_url: String?,
        val bio: String?,
        val seller_headline: String?,
        val location_tag: String?,
        val timezone: String?,
        val preferred_language: String?,
        val explicit_rating_avg: Double,
        val implicit_trust_score: Long,
        val last_calculated_at: Long?,
    )

    private fun resolveSellerRow(idOrUsername: String): SellerRow {
        val byId = Ids.parseOrNull(idOrUsername)?.let { repository.getSellerProfileById(it) }
        if (byId != null) return byId.toSellerRow()
        val byUsername = repository.getSellerProfileByUsername(idOrUsername)
            ?: notFound("User not found")
        return byUsername.toSellerRow()
    }

    private fun GetSellerProfileById.toSellerRow() = SellerRow(
        id = id,
        username = username,
        display_name = display_name,
        avatar_url = avatar_url,
        bio = bio,
        seller_headline = seller_headline,
        location_tag = location_tag,
        timezone = timezone,
        preferred_language = preferred_language,
        explicit_rating_avg = explicit_rating_avg,
        implicit_trust_score = implicit_trust_score,
        last_calculated_at = last_calculated_at,
    )

    private fun com.zula.GetSellerProfileByUsername.toSellerRow() = SellerRow(
        id = id,
        username = username,
        display_name = display_name,
        avatar_url = avatar_url,
        bio = bio,
        seller_headline = seller_headline,
        location_tag = location_tag,
        timezone = timezone,
        preferred_language = preferred_language,
        explicit_rating_avg = explicit_rating_avg,
        implicit_trust_score = implicit_trust_score,
        last_calculated_at = last_calculated_at,
    )

    private fun resolveTrustScores(
        userId: Uuid,
        cachedExplicit: Double,
        cachedImplicit: Long,
        lastCalculatedAt: Long?,
    ): Pair<Double, Int> {
        val now = Instant.now().epochSecond
        val stale = lastCalculatedAt == null || now - lastCalculatedAt > TRUST_STALE_SECONDS
        if (!stale) {
            return cachedExplicit to cachedImplicit.toInt()
        }
        val explicit = repository.calculateUserRatingAvg(userId)
        // Trust ledger not shipped yet — keep cached implicit on refresh.
        val implicit = cachedImplicit
        repository.upsertUserStatsValues(userId, explicit, implicit, now)
        return explicit to implicit.toInt()
    }

    private fun requireOwnProfile(actorId: Uuid, profileId: Uuid) {
        if (actorId != profileId) {
            forbidden("Can only modify own profile")
        }
    }

    companion object {
        private const val TRUST_STALE_SECONDS = 3600L
    }
}
