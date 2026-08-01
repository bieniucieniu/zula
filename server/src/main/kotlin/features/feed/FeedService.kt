package com.zula.features.feed

import com.zula.Feed_item_comments
import com.zula.Feed_items
import com.zula.core.http.badRequest
import com.zula.core.http.notFound
import com.zula.features.feed.domain.BookmarkResponse
import com.zula.features.feed.domain.CommentResponse
import com.zula.features.feed.domain.CreateCommentRequest
import com.zula.features.feed.domain.CreateFeedItemRequest
import com.zula.features.feed.domain.FeedCursor
import com.zula.features.feed.domain.FeedItemResponse
import com.zula.features.feed.domain.FeedMediaResponse
import com.zula.features.feed.domain.LikeResponse
import com.zula.features.feed.domain.ListCommentsResponse
import com.zula.features.feed.domain.ListFeedItemsResponse
import com.zula.features.feed.domain.ListTraitsResponse
import com.zula.features.feed.domain.TraitNode
import com.zula.features.groups.GroupService
import com.zula.features.media.MediaService
import com.zula.lib.id.Ids
import java.time.Instant
import kotlin.uuid.Uuid

class FeedService(
    private val repository: FeedRepository,
    private val traitSeeder: TraitSeeder,
    private val mediaService: MediaService,
    private val groupService: GroupService,
    private val publisher: FeedPublisher,
) {
    @Volatile
    private var traitsSeeded = false

    fun ensureTraitsSeeded() {
        if (traitsSeeded) return
        synchronized(this) {
            if (traitsSeeded) return
            traitSeeder.seedIfEmpty()
            traitsSeeded = true
        }
    }

    fun listTraits(): ListTraitsResponse {
        ensureTraitsSeeded()
        val all = repository.listAllTraits()
        val byParent = all.groupBy { it.parent_id }
        fun build(parentId: Uuid?): List<TraitNode> =
            (byParent[parentId] ?: emptyList()).map { t ->
                TraitNode(
                    id = t.id.toString(),
                    parentId = t.parent_id?.toString(),
                    slug = t.slug,
                    label = t.label,
                    sortOrder = t.sort_order,
                    children = build(t.id),
                )
            }
        return ListTraitsResponse(traits = build(null))
    }

    fun createItem(authorId: Uuid, req: CreateFeedItemRequest): FeedItemResponse {
        ensureTraitsSeeded()
        val kind = req.kind.trim().lowercase()
        if (kind !in KINDS) badRequest("kind must be one of ${KINDS.joinToString()}")
        val title = req.title.trim()
        if (title.isEmpty() || title.length > 200) badRequest("title must be 1–200 characters")
        val visibility = req.visibility.trim().lowercase()
        if (visibility !in VISIBILITIES) badRequest("visibility must be public, followers, or private")
        val locationMode = req.defaultLocationMode?.trim()?.lowercase()?.ifEmpty { null }
        if (locationMode != null && locationMode !in LOCATION_MODES) {
            badRequest("defaultLocationMode must be provider, client, or negotiated")
        }
        val traitIds = req.traitIds.map { raw ->
            Ids.parseOrNull(raw) ?: badRequest("traitIds must be UUIDs")
        }.distinct()
        traitIds.forEach { id ->
            repository.getTraitById(id) ?: badRequest("Unknown trait: $id")
        }
        val groupId = req.groupId?.let { raw ->
            Ids.parseOrNull(raw) ?: badRequest("groupId must be a UUID")
        }
        if (groupId != null) {
            groupService.getGroupOrNotFound(groupId)
            groupService.requireMember(groupId, authorId)
        }
        val mediaKeys = req.mediaObjectKeys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val now = Instant.now().epochSecond
        val item = repository.transaction {
            mediaService.commitKeys(authorId, mediaKeys)
            val created = insertFeedItem(
                authorId = authorId,
                groupId = groupId,
                kind = kind,
                title = title,
                body = req.body?.trim()?.ifEmpty { null },
                visibility = visibility,
                originTag = req.originTag?.trim()?.ifEmpty { null },
                destTag = req.destTag?.trim()?.ifEmpty { null },
                availableFrom = req.availableFrom,
                availableUntil = req.availableUntil,
                lastBumpedAt = now,
                defaultLocationMode = locationMode,
                updatedAt = now,
            )
            traitIds.forEach { insertFeedItemTrait(created.id, it) }
            mediaKeys.forEachIndexed { index, key ->
                insertFeedItemMedia(created.id, key, index.toLong())
            }
            created
        }
        publisher.publishItemCreated(item.id.toString())
        return item.toResponse(authorId)
    }

    fun getItem(id: Uuid, viewerId: Uuid?): FeedItemResponse {
        val item = repository.getFeedItem(id) ?: notFound("Feed item not found")
        enforceAuthorAccess(viewerId, item.author_id)
        if (item.visibility != "public" && viewerId != item.author_id) {
            notFound("Feed item not found")
        }
        return item.toResponse(viewerId)
    }

    fun listChrono(viewerId: Uuid?, cursorId: Uuid?, limit: Int): ListFeedItemsResponse =
        listPage(viewerId, cursorId, limit) { c, fetch ->
            repository.listPublicFeed(c, fetch)
        }

    fun listForYou(viewerId: Uuid?, cursorId: Uuid?, limit: Int): ListFeedItemsResponse =
        listChrono(viewerId, cursorId, limit)

    fun listByAuthor(
        authorId: Uuid,
        viewerId: Uuid?,
        cursorId: Uuid?,
        limit: Int,
    ): ListFeedItemsResponse {
        enforceAuthorAccess(viewerId, authorId)
        return listPage(viewerId, cursorId, limit) { c, fetch ->
            repository.listAuthorFeed(authorId, c, fetch)
        }
    }

    fun listByTrait(
        traitId: Uuid,
        viewerId: Uuid?,
        cursorId: Uuid?,
        limit: Int,
    ): ListFeedItemsResponse {
        ensureTraitsSeeded()
        repository.getTraitById(traitId) ?: notFound("Trait not found")
        return listPage(viewerId, cursorId, limit) { c, fetch ->
            repository.listTraitFeed(traitId, c, fetch)
        }
    }

    fun listByGroup(
        groupId: Uuid,
        viewerId: Uuid?,
        cursorId: Uuid?,
        limit: Int,
    ): ListFeedItemsResponse {
        val group = groupService.getGroupOrNotFound(groupId)
        if (group.visibility == "private") {
            if (viewerId == null || !groupService.isMember(groupId, viewerId)) {
                notFound("Group not found")
            }
        }
        return listPage(viewerId, cursorId, limit) { c, fetch ->
            repository.listGroupFeed(groupId, c, fetch)
        }
    }

    fun like(userId: Uuid, itemId: Uuid): LikeResponse {
        val item = requireVisibleItem(itemId, userId)
        if (repository.getLike(itemId, userId) != null) {
            return LikeResponse(feedItemId = itemId.toString(), liked = true, likeCount = item.like_count)
        }
        val now = Instant.now().epochSecond
        val updated = repository.transaction {
            insertLike(itemId, userId, now)
            bumpOnLike(itemId, now) ?: item
        }
        return LikeResponse(feedItemId = itemId.toString(), liked = true, likeCount = updated.like_count)
    }

    fun unlike(userId: Uuid, itemId: Uuid): LikeResponse {
        val item = requireVisibleItem(itemId, userId)
        if (repository.getLike(itemId, userId) == null) {
            return LikeResponse(feedItemId = itemId.toString(), liked = false, likeCount = item.like_count)
        }
        val now = Instant.now().epochSecond
        val updated = repository.transaction {
            deleteLike(itemId, userId)
            decrementLikeCount(itemId, now) ?: item
        }
        return LikeResponse(feedItemId = itemId.toString(), liked = false, likeCount = updated.like_count)
    }

    fun addComment(userId: Uuid, itemId: Uuid, req: CreateCommentRequest): CommentResponse {
        requireVisibleItem(itemId, userId)
        val body = req.body.trim()
        if (body.isEmpty() || body.length > 2000) badRequest("body must be 1–2000 characters")
        val now = Instant.now().epochSecond
        val comment = repository.transaction {
            val created = insertComment(itemId, userId, body)
            incrementCommentCount(itemId, now)
            created
        }
        return comment.toResponse()
    }

    fun listComments(
        itemId: Uuid,
        viewerId: Uuid?,
        cursorId: Uuid?,
        limit: Int,
    ): ListCommentsResponse {
        requireVisibleItem(itemId, viewerId)
        val pageSize = pageSize(limit)
        val rows = repository.listComments(itemId, cursorId, (pageSize + 1).toLong())
        val hasMore = rows.size > pageSize
        val page = (if (hasMore) rows.take(pageSize) else rows)
            .filterBlockedAuthors(viewerId) { it.author_id }
        val comments = page.map { it.toResponse() }
        return ListCommentsResponse(
            comments = comments,
            nextCursor = comments.lastOrNull()?.takeIf { hasMore }?.let { FeedCursor(it.id) },
            hasMore = hasMore,
        )
    }

    fun bookmark(userId: Uuid, itemId: Uuid): BookmarkResponse {
        requireVisibleItem(itemId, userId)
        if (repository.getBookmark(userId, itemId) == null) {
            repository.insertBookmark(userId, itemId, Instant.now().epochSecond)
        }
        return BookmarkResponse(feedItemId = itemId.toString(), bookmarked = true)
    }

    fun unbookmark(userId: Uuid, itemId: Uuid): BookmarkResponse {
        repository.deleteBookmark(userId, itemId)
        return BookmarkResponse(feedItemId = itemId.toString(), bookmarked = false)
    }

    fun listBookmarks(userId: Uuid, cursorId: Uuid?, limit: Int): ListFeedItemsResponse =
        listPage(userId, cursorId, limit) { c, fetch ->
            repository.listBookmarks(userId, c, fetch)
        }

    private fun listPage(
        viewerId: Uuid?,
        cursorId: Uuid?,
        limit: Int,
        fetch: (Uuid?, Long) -> List<Feed_items>,
    ): ListFeedItemsResponse {
        val pageSize = pageSize(limit)
        val rows = fetch(cursorId, (pageSize + 1).toLong())
        val hasMore = rows.size > pageSize
        val page = (if (hasMore) rows.take(pageSize) else rows)
            .filterBlockedAuthors(viewerId) { it.author_id }
        val items = page.map { it.toResponse(viewerId) }
        return ListFeedItemsResponse(
            items = items,
            nextCursor = items.lastOrNull()?.takeIf { hasMore }?.let { FeedCursor(it.id) },
            hasMore = hasMore,
        )
    }

    private fun requireVisibleItem(itemId: Uuid, viewerId: Uuid?): Feed_items {
        val item = repository.getFeedItem(itemId) ?: notFound("Feed item not found")
        enforceAuthorAccess(viewerId, item.author_id)
        if (item.status == "removed") notFound("Feed item not found")
        if (item.visibility != "public" && viewerId != item.author_id) {
            notFound("Feed item not found")
        }
        return item
    }

    private fun enforceAuthorAccess(viewerId: Uuid?, authorId: Uuid) {
        if (viewerId == null || viewerId == authorId) return
        if (repository.isBlockedEitherWay(viewerId, authorId)) {
            notFound("Feed item not found")
        }
    }

    private fun <T> List<T>.filterBlockedAuthors(viewerId: Uuid?, authorOf: (T) -> Uuid): List<T> {
        if (viewerId == null) return this
        return filterNot { repository.isBlockedEitherWay(viewerId, authorOf(it)) }
    }

    private fun Feed_items.toResponse(viewerId: Uuid?): FeedItemResponse {
        val traitIds = repository.listFeedItemTraits(id).map { it.toString() }
        val media = repository.listFeedItemMedia(id).map {
            FeedMediaResponse(
                objectKey = it.object_key,
                publicUrl = mediaService.publicUrl(it.object_key),
                sortOrder = it.sort_order,
            )
        }
        return FeedItemResponse(
            id = id.toString(),
            authorId = author_id.toString(),
            groupId = group_id?.toString(),
            kind = kind,
            title = title,
            body = body,
            status = status,
            visibility = visibility,
            originTag = origin_tag,
            destTag = dest_tag,
            availableFrom = available_from,
            availableUntil = available_until,
            likeCount = like_count,
            commentCount = comment_count,
            lastBumpedAt = last_bumped_at,
            defaultLocationMode = default_location_mode,
            updatedAt = updated_at,
            traitIds = traitIds,
            media = media,
            likedByViewer = viewerId?.let { repository.getLike(id, it) != null },
            bookmarkedByViewer = viewerId?.let { repository.getBookmark(it, id) != null },
        )
    }

    private fun Feed_item_comments.toResponse() = CommentResponse(
        id = id.toString(),
        feedItemId = feed_item_id.toString(),
        authorId = author_id.toString(),
        body = body,
    )

    private fun pageSize(limit: Int): Int = limit.coerceIn(1, 50)

    companion object {
        private val KINDS = setOf("need", "offer", "trip")
        private val VISIBILITIES = setOf("public", "followers", "private")
        private val LOCATION_MODES = setOf("provider", "client", "negotiated")
    }
}
