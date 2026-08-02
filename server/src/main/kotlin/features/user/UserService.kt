package com.zula.features.user

import com.zula.Documents
import com.zula.GetSellerProfileById
import com.zula.ListProfilePins
import com.zula.User_portfolio_items
import com.zula.core.http.badRequest
import com.zula.core.http.conflict
import com.zula.core.http.forbidden
import com.zula.core.http.notFound
import com.zula.core.http.unauthorized
import com.zula.features.media.MediaService
import com.zula.features.user.domain.BlockUserResponse
import com.zula.features.user.domain.DeletePortfolioItemResponse
import com.zula.features.user.domain.ListPortfolioItemsResponse
import com.zula.features.user.domain.ListPublicActivityResponse
import com.zula.features.user.domain.ListSellerReviewsResponse
import com.zula.features.user.domain.MyProfileResponse
import com.zula.features.user.domain.PinPortfolioItemRequest
import com.zula.features.user.domain.PinPortfolioItemResponse
import com.zula.features.user.domain.PortfolioItem
import com.zula.features.user.domain.ProfileCursor
import com.zula.features.user.domain.ReorderProfilePinsRequest
import com.zula.features.user.domain.ReorderProfilePinsResponse
import com.zula.features.user.domain.RichDocument
import com.zula.features.user.domain.SellerActivityCounts
import com.zula.features.user.domain.SellerProfileResponse
import com.zula.features.user.domain.SellerReviewPreview
import com.zula.features.user.domain.UnblockUserResponse
import com.zula.features.user.domain.UnpinPortfolioItemResponse
import com.zula.features.user.domain.UpdateMyProfileRequest
import com.zula.features.user.domain.UpdateTrustRequest
import com.zula.features.user.domain.UpsertPortfolioItemRequest
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
    private val mediaService: MediaService,
    private val adminConfig: UserAdminConfig = UserAdminConfig(),
) : UserProfileWriter {
    fun getSellerProfile(id: String, viewerId: Uuid?): SellerProfileResponse {
        val row = resolveSellerRow(id)
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
        val bio = repository.getProfileBioDocument(row.id)?.toRichDocument()
        val pins = repository.listProfilePins(row.id).map { it.toPortfolioItem() }
        val base = SellerProfileResponse(
            userId = row.id.toString(),
            username = row.username,
            displayName = row.display_name,
            avatarUrl = row.avatar_url,
            bio = bio,
            sellerHeadline = row.seller_headline,
            locationTag = row.location_tag,
            memberSince = Ids.createdAtInstant(row.id).toString(),
            explicitRatingAvg = explicit,
            implicitTrustScore = implicit,
            ratingCount = ratingCount,
            activity = activity,
            recentReviews = previews,
            pins = pins,
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
            timezone = req.timezone?.trim(),
            preferredLanguage = req.preferredLanguage?.trim()?.lowercase(),
            locationTag = req.locationTag?.trim(),
            sellerHeadline = req.sellerHeadline?.trim(),
            updatedAt = now,
        )
        if (req.bio != null) {
            upsertProfileBioDocument(
                ownerId = ownerId,
                sourceMarkdown = req.bio,
                expectedRevision = req.bioExpectedRevision?.toLong(),
                now = now,
            )
        }
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

    fun getLegacyUserProfile(id: String, viewerId: Uuid?): UserResponse {
        val seller = getSellerProfile(id, viewerId)
        return UserResponse(
            userId = seller.userId,
            username = seller.username,
            explicitRatingAvg = seller.explicitRatingAvg,
            implicitTrustScore = seller.implicitTrustScore,
        )
    }

    fun listSellerReviews(
        id: String,
        viewerId: Uuid?,
        cursorId: Uuid?,
        limit: Int,
    ): ListSellerReviewsResponse {
        val row = resolveSellerRow(id)
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

    fun listPortfolio(
        id: String,
        viewerId: Uuid?,
        cursorId: Uuid?,
        limit: Int,
    ): ListPortfolioItemsResponse {
        val targetId = resolveUserId(id, viewerId)
        enforcePublicTargetAccess(viewerId, targetId)
        repository.getUserById(targetId) ?: notFound("User not found")
        val includeUnlisted = viewerId != null && viewerId == targetId
        val pageSize = limit.coerceIn(1, 50)
        val rows = repository.listPortfolioItems(targetId, includeUnlisted, cursorId, pageSize + 1L)
        val hasMore = rows.size > pageSize
        val page = if (hasMore) rows.take(pageSize) else rows
        val items = page.map { it.toPortfolioItem(includeBody = false) }
        return ListPortfolioItemsResponse(
            items = items,
            nextCursor = items.lastOrNull()?.takeIf { hasMore }?.let { ProfileCursor(it.id) },
            hasMore = hasMore,
        )
    }

    fun upsertPortfolioItem(actorId: Uuid, id: String, req: UpsertPortfolioItemRequest): PortfolioItem {
        val targetId = resolveUserId(id, actorId)
        requireOwnProfile(actorId, targetId)
        PortfolioValidation.validateUpsert(req)
        val now = Instant.now().epochSecond
        val kind = req.kind.trim()
        val title = req.title.trim()
        val visibility = req.visibility.trim()
        val summary = req.summary?.trim()?.ifEmpty { null }
        val externalUrl = req.externalUrl?.trim()?.ifEmpty { null }
        val cover = req.coverObjectKey?.trim()?.ifEmpty { null }?.let { key ->
            mediaService.requireOwnedUploadKey(targetId, key)
        }
        val tradeId = req.tradeId?.let { Ids.parseOrNull(it) ?: badRequest("tradeId must be a UUID") }
        return repository.transaction {
            val existing = req.id?.let { raw ->
                val itemId = Ids.parseOrNull(raw) ?: badRequest("id must be a UUID")
                getPortfolioItem(itemId) ?: notFound("Portfolio item not found")
            }
            if (existing != null && existing.user_id != targetId) {
                forbidden("Can only modify own portfolio items")
            }
            val previousCover = existing?.cover_object_key
            if (cover != null) {
                mediaService.commitKeys(targetId, listOf(cover))
            }
            val bodyId = upsertBodyDocument(
                ownerId = targetId,
                existingBodyId = existing?.body_document_id,
                markdown = req.bodyMarkdown,
                expectedRevision = req.bodyExpectedRevision?.toLong(),
                now = now,
            )
            val resolvedCover = cover ?: previousCover
            val item = if (existing == null) {
                insertPortfolioItem(
                    userId = targetId,
                    kind = kind,
                    title = title,
                    summary = summary,
                    bodyDocumentId = bodyId,
                    feedItemId = null,
                    tradeId = tradeId,
                    externalUrl = externalUrl,
                    coverObjectKey = resolvedCover,
                    sortOrder = req.sortOrder.toLong(),
                    visibility = visibility,
                    updatedAt = now,
                )
            } else {
                updatePortfolioItem(
                    id = existing.id,
                    userId = targetId,
                    kind = kind,
                    title = title,
                    summary = summary,
                    bodyDocumentId = bodyId ?: existing.body_document_id,
                    feedItemId = null,
                    tradeId = tradeId,
                    externalUrl = externalUrl,
                    coverObjectKey = if (req.coverObjectKey != null) cover else previousCover,
                    sortOrder = req.sortOrder.toLong(),
                    visibility = visibility,
                    updatedAt = now,
                ) ?: notFound("Portfolio item not found")
            }
            if (req.coverObjectKey != null && previousCover != null && previousCover != cover) {
                mediaService.releaseKey(previousCover)
            }
            val resolvedBodyId = item.body_document_id
            val body = resolvedBodyId?.let { getDocument(it)?.toRichDocument() }
            item.toPortfolioItem(includeBody = true, body = body)
        }
    }

    fun deletePortfolioItem(actorId: Uuid, id: String, itemId: Uuid): DeletePortfolioItemResponse {
        val targetId = resolveUserId(id, actorId)
        requireOwnProfile(actorId, targetId)
        val existing = repository.getPortfolioItem(itemId) ?: notFound("Portfolio item not found")
        if (existing.user_id != targetId) notFound("Portfolio item not found")
        repository.deletePortfolioItem(itemId, targetId) ?: notFound("Portfolio item not found")
        mediaService.releaseKey(existing.cover_object_key)
        return DeletePortfolioItemResponse(id = itemId.toString())
    }

    fun pinPortfolioItem(actorId: Uuid, id: String, req: PinPortfolioItemRequest): PinPortfolioItemResponse {
        val targetId = resolveUserId(id, actorId)
        requireOwnProfile(actorId, targetId)
        val itemId = Ids.parseOrNull(req.portfolioItemId) ?: badRequest("portfolioItemId must be a UUID")
        val item = repository.getPortfolioItem(itemId) ?: notFound("Portfolio item not found")
        if (item.user_id != targetId) forbidden("Can only pin own portfolio items")
        if (item.visibility != "public") badRequest("Only public portfolio items can be pinned")
        val count = repository.countProfilePins(targetId)
        val alreadyPinned = repository.listProfilePins(targetId).any { it.id == itemId }
        if (!alreadyPinned && count >= PortfolioValidation.MAX_PINS) {
            badRequest("Maximum ${PortfolioValidation.MAX_PINS} pins allowed")
        }
        val sortOrder = count
        repository.insertProfilePin(targetId, itemId, sortOrder)
        return PinPortfolioItemResponse(portfolioItemId = itemId.toString(), sortOrder = sortOrder.toInt())
    }

    fun unpinPortfolioItem(actorId: Uuid, id: String, itemId: Uuid): UnpinPortfolioItemResponse {
        val targetId = resolveUserId(id, actorId)
        requireOwnProfile(actorId, targetId)
        repository.deleteProfilePin(targetId, itemId) ?: notFound("Pin not found")
        return UnpinPortfolioItemResponse(portfolioItemId = itemId.toString())
    }

    fun reorderProfilePins(
        actorId: Uuid,
        id: String,
        req: ReorderProfilePinsRequest,
    ): ReorderProfilePinsResponse {
        val targetId = resolveUserId(id, actorId)
        requireOwnProfile(actorId, targetId)
        if (req.portfolioItemIds.size > PortfolioValidation.MAX_PINS) {
            badRequest("Maximum ${PortfolioValidation.MAX_PINS} pins allowed")
        }
        val ids = req.portfolioItemIds.map { raw ->
            Ids.parseOrNull(raw) ?: badRequest("portfolioItemIds must be UUIDs")
        }
        if (ids.toSet().size != ids.size) badRequest("portfolioItemIds must be unique")
        ids.forEach { itemId ->
            val item = repository.getPortfolioItem(itemId) ?: notFound("Portfolio item not found")
            if (item.user_id != targetId) forbidden("Can only pin own portfolio items")
            if (item.visibility != "public") badRequest("Only public portfolio items can be pinned")
        }
        repository.transaction {
            replaceProfilePins(targetId, ids)
        }
        return ReorderProfilePinsResponse(portfolioItemIds = ids.map { it.toString() })
    }

    fun listPublicActivity(id: String, viewerId: Uuid?): ListPublicActivityResponse {
        val targetId = resolveUserId(id, viewerId)
        enforcePublicTargetAccess(viewerId, targetId)
        repository.getUserById(targetId) ?: notFound("User not found")
        return ListPublicActivityResponse()
    }

    fun isAdmin(userId: Uuid): Boolean =
        adminConfig.isAdminGoogleSubject(repository.findGoogleProviderUserId(userId))

    /**
     * Resolve path `{id}`: `me` → caller UUID (auth required); UUID; or username.
     */
    fun resolveUserId(id: String, viewerId: Uuid?): Uuid {
        if (id == "me") {
            return viewerId ?: unauthorized("Authentication required")
        }
        Ids.parseOrNull(id)?.let { parsed ->
            repository.getUserById(parsed) ?: notFound("User not found")
            return parsed
        }
        return repository.findUserByUsername(id)?.id
            ?: notFound("User not found")
    }

    private fun upsertProfileBioDocument(
        ownerId: Uuid,
        sourceMarkdown: String,
        expectedRevision: Long?,
        now: Long,
    ) {
        repository.transaction {
            val existing = getProfileBioDocument(ownerId)
            if (existing == null) {
                val created = createDocument(ownerId, sourceMarkdown, now)
                insertDocumentRevision(created.id, created.revision, created.source, now)
                upsertProfileBio(ownerId, created.id, now)
            } else {
                val updated = updateDocument(
                    id = existing.id,
                    ownerUserId = ownerId,
                    source = sourceMarkdown,
                    updatedAt = now,
                    expectedRevision = expectedRevision,
                ) ?: conflict("Document revision conflict")
                insertDocumentRevision(updated.id, updated.revision, updated.source, now)
                upsertProfileBio(ownerId, updated.id, now)
            }
        }
    }

    private fun UserRepository.upsertBodyDocument(
        ownerId: Uuid,
        existingBodyId: Uuid?,
        markdown: String?,
        expectedRevision: Long?,
        now: Long,
    ): Uuid? {
        if (markdown == null) return existingBodyId
        return if (existingBodyId == null) {
            val created = createDocument(ownerId, markdown, now)
            insertDocumentRevision(created.id, created.revision, created.source, now)
            created.id
        } else {
            val updated = updateDocument(
                id = existingBodyId,
                ownerUserId = ownerId,
                source = markdown,
                updatedAt = now,
                expectedRevision = expectedRevision,
            ) ?: conflict("Document revision conflict")
            insertDocumentRevision(updated.id, updated.revision, updated.source, now)
            updated.id
        }
    }

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
        val seller_headline: String?,
        val location_tag: String?,
        val timezone: String?,
        val preferred_language: String?,
        val explicit_rating_avg: Double,
        val implicit_trust_score: Long,
        val last_calculated_at: Long?,
    )

    private fun resolveSellerRow(id: String): SellerRow {
        val byId = Ids.parseOrNull(id)?.let { repository.getSellerProfileById(it) }
        if (byId != null) return byId.toSellerRow()
        val byUsername = repository.getSellerProfileByUsername(id)
            ?: notFound("User not found")
        return byUsername.toSellerRow()
    }

    private fun GetSellerProfileById.toSellerRow() = SellerRow(
        id = id,
        username = username,
        display_name = display_name,
        avatar_url = avatar_url,
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
        val implicit = cachedImplicit
        repository.upsertUserStatsValues(userId, explicit, implicit, now)
        return explicit to implicit.toInt()
    }

    private fun requireOwnProfile(actorId: Uuid, profileId: Uuid) {
        if (actorId != profileId) {
            forbidden("Can only modify own profile")
        }
    }

    private fun Documents.toRichDocument() = RichDocument(
        id = id.toString(),
        format = format,
        sourceMarkdown = source,
        revision = revision.toInt(),
        updatedAt = Instant.ofEpochSecond(updated_at).toString(),
    )

    private fun User_portfolio_items.toPortfolioItem(
        includeBody: Boolean,
        body: RichDocument? = null,
    ) = PortfolioItem(
        id = id.toString(),
        kind = kind,
        title = title,
        summary = summary,
        body = if (includeBody) body else null,
        feedItemId = feed_item_id?.toString(),
        tradeId = trade_id?.toString(),
        externalUrl = external_url,
        coverUrl = mediaService.publicUrl(cover_object_key),
        visibility = visibility,
        sortOrder = sort_order.toInt(),
        createdAt = Ids.createdAtInstant(id).toString(),
    )

    private fun ListProfilePins.toPortfolioItem() = PortfolioItem(
        id = id.toString(),
        kind = kind,
        title = title,
        summary = summary,
        body = null,
        feedItemId = feed_item_id?.toString(),
        tradeId = trade_id?.toString(),
        externalUrl = external_url,
        coverUrl = mediaService.publicUrl(cover_object_key),
        visibility = visibility,
        sortOrder = pin_sort_order.toInt(),
        createdAt = Ids.createdAtInstant(id).toString(),
    )

    companion object {
        private const val TRUST_STALE_SECONDS = 3600L
    }
}
