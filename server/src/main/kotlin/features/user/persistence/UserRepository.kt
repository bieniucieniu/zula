package com.zula.features.user.persistence

import com.zula.Database
import com.zula.GetSellerProfileById
import com.zula.GetSellerProfileByUsername
import com.zula.ListRecentSellerReviews
import com.zula.ListSellerReviewsFirstPage
import com.zula.ListSellerReviewsPage
import com.zula.Seller_activity_stats
import com.zula.User_profiles
import com.zula.User_stats
import com.zula.Users
import kotlin.uuid.Uuid

class UserRepository(
    private val database: Database,
) {
    private val queries inline get() = database.userQueries

    fun getUserById(userId: Uuid): Users? =
        queries.getUserById(userId).executeAsOneOrNull()

    fun findUserByUsername(username: String): Users? =
        queries.findUserByUsername(username).executeAsOneOrNull()

    fun getProfile(userId: Uuid): User_profiles? =
        queries.getUserProfile(userId).executeAsOneOrNull()

    fun ensureUserProfile(userId: Uuid, displayName: String?, updatedAt: Long) {
        queries.ensureUserProfile(userId, displayName, updatedAt)
    }

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

    fun getSellerProfileById(userId: Uuid): GetSellerProfileById? =
        queries.getSellerProfileById(userId).executeAsOneOrNull()

    fun getSellerProfileByUsername(username: String): GetSellerProfileByUsername? =
        queries.getSellerProfileByUsername(username).executeAsOneOrNull()

    fun getUserStats(userId: Uuid): User_stats? =
        queries.getUserStats(userId).executeAsOneOrNull()

    fun upsertUserStatsValues(
        userId: Uuid,
        explicitRatingAvg: Double,
        implicitTrustScore: Long,
        lastCalculatedAt: Long,
    ) {
        queries.upsertUserStatsValues(userId, explicitRatingAvg, implicitTrustScore, lastCalculatedAt)
    }

    fun updateImplicitTrustScore(userId: Uuid, score: Long, lastCalculatedAt: Long) {
        queries.updateImplicitTrustScore(score, lastCalculatedAt, userId)
    }

    fun calculateUserRatingAvg(userId: Uuid): Double =
        queries.calculateUserRatingAvg(userId).executeAsOne()

    fun countUserRatings(userId: Uuid): Long =
        queries.countUserRatings(userId).executeAsOne()

    fun listRecentSellerReviews(userId: Uuid, limit: Long): List<ListRecentSellerReviews> =
        queries.listRecentSellerReviews(userId, limit).executeAsList()

    fun listSellerReviewsFirstPage(userId: Uuid, limit: Long): List<ListSellerReviewsFirstPage> =
        queries.listSellerReviewsFirstPage(userId, limit).executeAsList()

    fun listSellerReviewsPage(userId: Uuid, cursorId: Uuid, limit: Long): List<ListSellerReviewsPage> =
        queries.listSellerReviewsPage(userId, cursorId, limit).executeAsList()

    fun getSellerActivityStats(userId: Uuid): Seller_activity_stats? =
        queries.getSellerActivityStats(userId).executeAsOneOrNull()

    fun isBlockedEitherDirection(a: Uuid, b: Uuid): Boolean =
        queries.isBlockedEitherDirection(a, b, b, a).executeAsOne()

    fun hasViewerBlockedSeller(viewerId: Uuid, sellerId: Uuid): Boolean =
        queries.hasViewerBlockedSeller(viewerId, sellerId).executeAsOne()

    fun hasSellerBlockedViewer(sellerId: Uuid, viewerId: Uuid): Boolean =
        queries.hasSellerBlockedViewer(sellerId, viewerId).executeAsOne()

    fun blockUser(blockerId: Uuid, blockedId: Uuid) {
        queries.blockUser(blockerId, blockedId)
    }

    fun unblockUser(blockerId: Uuid, blockedId: Uuid) {
        queries.unblockUser(blockerId, blockedId)
    }

    fun primaryEmailForUser(userId: Uuid): String? =
        queries.primaryEmailForUser(userId).executeAsOneOrNull()

    fun findGoogleProviderUserId(userId: Uuid): String? =
        queries.findGoogleProviderUserId(userId).executeAsOneOrNull()

    fun insertUserRating(reviewerId: Uuid, revieweeId: Uuid, rating: Long, comment: String?): Uuid =
        queries.insertUserRating(reviewerId, revieweeId, rating, comment).executeAsOne()
}
