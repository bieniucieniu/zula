package com.zula.features.feed.domain

import kotlinx.serialization.Serializable

@Serializable
data class FeedCursor(
    val id: String,
)

@Serializable
data class CreateFeedItemRequest(
    val kind: String,
    val title: String,
    val body: String? = null,
    val traitIds: List<String> = emptyList(),
    val mediaObjectKeys: List<String> = emptyList(),
    val groupId: String? = null,
    val visibility: String = "public",
    val originTag: String? = null,
    val destTag: String? = null,
    val availableFrom: Long? = null,
    val availableUntil: Long? = null,
    val defaultLocationMode: String? = null,
)

@Serializable
data class FeedMediaResponse(
    val objectKey: String,
    val publicUrl: String? = null,
    val sortOrder: Long,
)

@Serializable
data class FeedItemResponse(
    val id: String,
    val authorId: String,
    val groupId: String? = null,
    val kind: String,
    val title: String,
    val body: String? = null,
    val status: String,
    val visibility: String,
    val originTag: String? = null,
    val destTag: String? = null,
    val availableFrom: Long? = null,
    val availableUntil: Long? = null,
    val likeCount: Long,
    val commentCount: Long,
    val lastBumpedAt: Long,
    val defaultLocationMode: String? = null,
    val updatedAt: Long,
    val traitIds: List<String> = emptyList(),
    val media: List<FeedMediaResponse> = emptyList(),
    val likedByViewer: Boolean? = null,
    val bookmarkedByViewer: Boolean? = null,
)

@Serializable
data class ListFeedItemsResponse(
    val items: List<FeedItemResponse>,
    val nextCursor: FeedCursor? = null,
    val hasMore: Boolean,
)

@Serializable
data class TraitNode(
    val id: String,
    val parentId: String? = null,
    val slug: String,
    val label: String,
    val sortOrder: Long,
    val children: List<TraitNode> = emptyList(),
)

@Serializable
data class ListTraitsResponse(
    val traits: List<TraitNode>,
)

@Serializable
data class CreateCommentRequest(
    val body: String,
)

@Serializable
data class CommentResponse(
    val id: String,
    val feedItemId: String,
    val authorId: String,
    val body: String,
)

@Serializable
data class ListCommentsResponse(
    val comments: List<CommentResponse>,
    val nextCursor: FeedCursor? = null,
    val hasMore: Boolean,
)

@Serializable
data class LikeResponse(
    val feedItemId: String,
    val liked: Boolean,
    val likeCount: Long,
)

@Serializable
data class BookmarkResponse(
    val feedItemId: String,
    val bookmarked: Boolean,
)
