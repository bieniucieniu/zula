package com.zula.features.user.domain

import kotlinx.serialization.Serializable

@Serializable
data class ProfileCursor(
    val id: String,
)

@Serializable
data class SellerActivityCounts(
    val activeOffers: Int,
    val activeNeeds: Int,
    val activeTrips: Int,
    val fulfilledItems: Int,
    val completedTrades: Int = 0,
)

@Serializable
data class SellerReviewPreview(
    val id: String,
    val reviewerUsername: String,
    val rating: Int,
    val comment: String? = null,
    val createdAt: String,
)

@Serializable
data class SellerProfileResponse(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val sellerHeadline: String? = null,
    val locationTag: String? = null,
    val memberSince: String,
    val explicitRatingAvg: Double,
    val implicitTrustScore: Int,
    val ratingCount: Int,
    val activity: SellerActivityCounts? = null,
    val recentReviews: List<SellerReviewPreview> = emptyList(),
    val viewerHasBlocked: Boolean? = null,
    val viewerIsBlocked: Boolean? = null,
)

@Serializable
data class MyProfileResponse(
    val public: SellerProfileResponse,
    val timezone: String? = null,
    val preferredLanguage: String? = null,
    val email: String? = null,
    val isAdmin: Boolean = false,
)

@Serializable
data class UpdateMyProfileRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val sellerHeadline: String? = null,
    val locationTag: String? = null,
    val timezone: String? = null,
    val preferredLanguage: String? = null,
)

@Serializable
data class ListSellerReviewsResponse(
    val reviews: List<SellerReviewPreview>,
    val nextCursor: ProfileCursor? = null,
    val hasMore: Boolean,
)

@Serializable
data class UserResponse(
    val userId: String,
    val username: String,
    val explicitRatingAvg: Double,
    val implicitTrustScore: Int,
)

@Serializable
data class BlockUserResponse(
    val blockedUserId: String,
)

@Serializable
data class UnblockUserResponse(
    val unblockedUserId: String,
)

@Serializable
data class UpdateTrustRequest(
    val delta: Int,
)
