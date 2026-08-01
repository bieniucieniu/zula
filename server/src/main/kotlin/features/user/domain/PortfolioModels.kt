package com.zula.features.user.domain

import kotlinx.serialization.Serializable

@Serializable
data class RichDocument(
    val id: String,
    val format: String = "markdown",
    val sourceMarkdown: String,
    val revision: Int,
    val updatedAt: String,
)

@Serializable
data class PortfolioItem(
    val id: String,
    val kind: String,
    val title: String,
    val summary: String? = null,
    val body: RichDocument? = null,
    val feedItemId: String? = null,
    val tradeId: String? = null,
    val externalUrl: String? = null,
    val coverUrl: String? = null,
    val visibility: String,
    val sortOrder: Int,
    val createdAt: String,
)

@Serializable
data class UpsertPortfolioItemRequest(
    val id: String? = null,
    val kind: String,
    val title: String,
    val summary: String? = null,
    val bodyMarkdown: String? = null,
    val bodyExpectedRevision: Int? = null,
    val feedItemId: String? = null,
    val tradeId: String? = null,
    val externalUrl: String? = null,
    val coverObjectKey: String? = null,
    val sortOrder: Int = 0,
    val visibility: String = "public",
)

@Serializable
data class DeletePortfolioItemResponse(
    val id: String,
)

@Serializable
data class ListPortfolioItemsResponse(
    val items: List<PortfolioItem>,
    val nextCursor: ProfileCursor? = null,
    val hasMore: Boolean,
)

@Serializable
data class PinPortfolioItemRequest(
    val portfolioItemId: String,
)

@Serializable
data class PinPortfolioItemResponse(
    val portfolioItemId: String,
    val sortOrder: Int,
)

@Serializable
data class UnpinPortfolioItemResponse(
    val portfolioItemId: String,
)

@Serializable
data class ReorderProfilePinsRequest(
    val portfolioItemIds: List<String>,
)

@Serializable
data class ReorderProfilePinsResponse(
    val portfolioItemIds: List<String>,
)

@Serializable
data class PublicActivityItem(
    val id: String,
    val kind: String,
    val title: String,
    val occurredAt: String,
)

@Serializable
data class ListPublicActivityResponse(
    val items: List<PublicActivityItem> = emptyList(),
    val nextCursor: ProfileCursor? = null,
    val hasMore: Boolean = false,
)
