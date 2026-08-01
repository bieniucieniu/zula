package com.zula.features.user.persistence

import com.zula.Database
import com.zula.Documents
import com.zula.GetSellerProfileById
import com.zula.GetSellerProfileByUsername
import com.zula.ListProfilePins
import com.zula.ListRecentSellerReviews
import com.zula.ListSellerReviewsFirstPage
import com.zula.ListSellerReviewsPage
import com.zula.Seller_activity_stats
import com.zula.User_portfolio_items
import com.zula.User_profiles
import com.zula.User_stats
import com.zula.Users
import kotlin.uuid.Uuid

class UserRepository(
    private val database: Database,
) {
    private val queries inline get() = database.userQueries
    private val portfolio inline get() = database.portfolioQueries

    fun <T> transaction(block: UserRepository.() -> T): T =
        database.transactionWithResult { this@UserRepository.block() }

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
        timezone: String?,
        preferredLanguage: String?,
        locationTag: String?,
        sellerHeadline: String?,
        updatedAt: Long,
    ) {
        queries.updateUserProfile(
            display_name = displayName,
            avatar_url = avatarUrl,
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

    fun createDocument(ownerUserId: Uuid, source: String, updatedAt: Long): Documents =
        portfolio.createDocument(ownerUserId, source, updatedAt).executeAsOne()

    fun getDocument(id: Uuid): Documents? =
        portfolio.getDocument(id).executeAsOneOrNull()

    fun updateDocument(
        id: Uuid,
        ownerUserId: Uuid,
        source: String,
        updatedAt: Long,
        expectedRevision: Long?,
    ): Documents? =
        if (expectedRevision == null) {
            portfolio.updateDocument(source, updatedAt, id, ownerUserId).executeAsOneOrNull()
        } else {
            portfolio.updateDocumentIfRevision(source, updatedAt, id, ownerUserId, expectedRevision)
                .executeAsOneOrNull()
        }

    fun insertDocumentRevision(documentId: Uuid, revision: Long, source: String, createdAt: Long) {
        portfolio.insertDocumentRevision(documentId, revision, source, createdAt)
    }

    fun getProfileBioDocument(userId: Uuid): Documents? =
        portfolio.getProfileBioDocument(userId).executeAsOneOrNull()

    fun upsertProfileBio(userId: Uuid, documentId: Uuid, updatedAt: Long) {
        portfolio.upsertProfileBio(userId, documentId, updatedAt)
    }

    fun insertPortfolioItem(
        userId: Uuid,
        kind: String,
        title: String,
        summary: String?,
        bodyDocumentId: Uuid?,
        feedItemId: Uuid?,
        tradeId: Uuid?,
        externalUrl: String?,
        coverObjectKey: String?,
        sortOrder: Long,
        visibility: String,
        updatedAt: Long,
    ): User_portfolio_items =
        portfolio.insertPortfolioItem(
            userId,
            kind,
            title,
            summary,
            bodyDocumentId,
            feedItemId,
            tradeId,
            externalUrl,
            coverObjectKey,
            sortOrder,
            visibility,
            updatedAt,
        ).executeAsOne()

    fun updatePortfolioItem(
        id: Uuid,
        userId: Uuid,
        kind: String,
        title: String,
        summary: String?,
        bodyDocumentId: Uuid?,
        feedItemId: Uuid?,
        tradeId: Uuid?,
        externalUrl: String?,
        coverObjectKey: String?,
        sortOrder: Long,
        visibility: String,
        updatedAt: Long,
    ): User_portfolio_items? =
        portfolio.updatePortfolioItem(
            kind,
            title,
            summary,
            bodyDocumentId,
            feedItemId,
            tradeId,
            externalUrl,
            coverObjectKey,
            sortOrder,
            visibility,
            updatedAt,
            id,
            userId,
        ).executeAsOneOrNull()

    fun getPortfolioItem(id: Uuid): User_portfolio_items? =
        portfolio.getPortfolioItem(id).executeAsOneOrNull()

    fun deletePortfolioItem(id: Uuid, userId: Uuid): Uuid? =
        portfolio.deletePortfolioItem(id, userId).executeAsOneOrNull()

    fun listPortfolioItems(
        userId: Uuid,
        includeUnlisted: Boolean,
        cursorId: Uuid?,
        limit: Long,
    ): List<User_portfolio_items> =
        if (includeUnlisted) {
            if (cursorId == null) {
                portfolio.listPortfolioItemsOwnerFirstPage(userId, limit).executeAsList()
            } else {
                portfolio.listPortfolioItemsOwnerPage(userId, cursorId, limit).executeAsList()
            }
        } else {
            if (cursorId == null) {
                portfolio.listPortfolioItemsPublicFirstPage(userId, limit).executeAsList()
            } else {
                portfolio.listPortfolioItemsPublicPage(userId, cursorId, limit).executeAsList()
            }
        }

    fun listProfilePins(userId: Uuid): List<ListProfilePins> =
        portfolio.listProfilePins(userId).executeAsList()

    fun countProfilePins(userId: Uuid): Long =
        portfolio.countProfilePins(userId).executeAsOne()

    fun insertProfilePin(userId: Uuid, portfolioItemId: Uuid, sortOrder: Long) {
        portfolio.insertProfilePin(userId, portfolioItemId, sortOrder)
    }

    fun deleteProfilePin(userId: Uuid, portfolioItemId: Uuid): Uuid? =
        portfolio.deleteProfilePin(userId, portfolioItemId).executeAsOneOrNull()

    fun replaceProfilePins(userId: Uuid, portfolioItemIds: List<Uuid>) {
        portfolio.deleteAllProfilePins(userId)
        portfolioItemIds.forEachIndexed { index, itemId ->
            portfolio.insertProfilePinOrdered(userId, itemId, index.toLong())
        }
    }
}
