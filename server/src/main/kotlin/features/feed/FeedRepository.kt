package com.zula.features.feed

import com.zula.Database
import com.zula.Feed_item_comments
import com.zula.Feed_item_likes
import com.zula.Feed_item_media
import com.zula.Feed_items
import com.zula.Traits
import kotlin.uuid.Uuid

class FeedRepository(
    private val database: Database,
) {
    private val feed inline get() = database.feedQueries
    private val traits inline get() = database.traitsQueries

    fun <T> transaction(block: FeedRepository.() -> T): T =
        database.transactionWithResult { this@FeedRepository.block() }

    fun insertFeedItem(
        authorId: Uuid,
        groupId: Uuid?,
        kind: String,
        title: String,
        body: String?,
        visibility: String,
        originTag: String?,
        destTag: String?,
        availableFrom: Long?,
        availableUntil: Long?,
        lastBumpedAt: Long,
        defaultLocationMode: String?,
        updatedAt: Long,
    ): Feed_items =
        feed.insertFeedItem(
            authorId,
            groupId,
            kind,
            title,
            body,
            visibility,
            originTag,
            destTag,
            availableFrom,
            availableUntil,
            lastBumpedAt,
            defaultLocationMode,
            updatedAt,
        ).executeAsOne()

    fun getFeedItem(id: Uuid): Feed_items? =
        feed.getFeedItem(id).executeAsOneOrNull()

    fun insertFeedItemTrait(feedItemId: Uuid, traitId: Uuid) {
        feed.insertFeedItemTrait(feedItemId, traitId)
    }

    fun insertFeedItemMedia(feedItemId: Uuid, objectKey: String, sortOrder: Long): Feed_item_media =
        feed.insertFeedItemMedia(feedItemId, objectKey, sortOrder).executeAsOne()

    fun listFeedItemTraits(feedItemId: Uuid): List<Uuid> =
        feed.listFeedItemTraits(feedItemId).executeAsList()

    fun listFeedItemMedia(feedItemId: Uuid): List<Feed_item_media> =
        feed.listFeedItemMedia(feedItemId).executeAsList()

    fun listPublicFeed(cursorId: Uuid?, limit: Long): List<Feed_items> =
        if (cursorId == null) {
            feed.listPublicFeedFirst(limit).executeAsList()
        } else {
            feed.listPublicFeedPage(cursorId, limit).executeAsList()
        }

    fun listAuthorFeed(authorId: Uuid, cursorId: Uuid?, limit: Long): List<Feed_items> =
        if (cursorId == null) {
            feed.listAuthorFeedFirst(authorId, limit).executeAsList()
        } else {
            feed.listAuthorFeedPage(authorId, cursorId, limit).executeAsList()
        }

    fun listTraitFeed(traitId: Uuid, cursorId: Uuid?, limit: Long): List<Feed_items> =
        if (cursorId == null) {
            feed.listTraitFeedFirst(traitId, limit).executeAsList()
        } else {
            feed.listTraitFeedPage(traitId, cursorId, limit).executeAsList()
        }

    fun listGroupFeed(groupId: Uuid, cursorId: Uuid?, limit: Long): List<Feed_items> =
        if (cursorId == null) {
            feed.listGroupFeedFirst(groupId, limit).executeAsList()
        } else {
            feed.listGroupFeedPage(groupId, cursorId, limit).executeAsList()
        }

    fun insertLike(feedItemId: Uuid, userId: Uuid, createdAt: Long) {
        feed.insertLike(feedItemId, userId, createdAt)
    }

    fun deleteLike(feedItemId: Uuid, userId: Uuid) {
        feed.deleteLike(feedItemId, userId)
    }

    fun getLike(feedItemId: Uuid, userId: Uuid): Feed_item_likes? =
        feed.getLike(feedItemId, userId).executeAsOneOrNull()

    fun bumpOnLike(id: Uuid, now: Long): Feed_items? =
        feed.bumpOnLike(now, now, id).executeAsOneOrNull()

    fun decrementLikeCount(id: Uuid, updatedAt: Long): Feed_items? =
        feed.decrementLikeCount(updatedAt, id).executeAsOneOrNull()

    fun insertComment(feedItemId: Uuid, authorId: Uuid, body: String): Feed_item_comments =
        feed.insertComment(feedItemId, authorId, body).executeAsOne()

    fun incrementCommentCount(id: Uuid, updatedAt: Long): Feed_items? =
        feed.incrementCommentCount(updatedAt, id).executeAsOneOrNull()

    fun listComments(feedItemId: Uuid, cursorId: Uuid?, limit: Long): List<Feed_item_comments> =
        if (cursorId == null) {
            feed.listCommentsFirst(feedItemId, limit).executeAsList()
        } else {
            feed.listCommentsPage(feedItemId, cursorId, limit).executeAsList()
        }

    fun insertBookmark(userId: Uuid, feedItemId: Uuid, createdAt: Long) {
        feed.insertBookmark(userId, feedItemId, createdAt)
    }

    fun deleteBookmark(userId: Uuid, feedItemId: Uuid) {
        feed.deleteBookmark(userId, feedItemId)
    }

    fun getBookmark(userId: Uuid, feedItemId: Uuid) =
        feed.getBookmark(userId, feedItemId).executeAsOneOrNull()

    fun listBookmarks(userId: Uuid, cursorId: Uuid?, limit: Long): List<Feed_items> =
        if (cursorId == null) {
            feed.listBookmarksFirst(userId, limit).executeAsList()
        } else {
            feed.listBookmarksPage(userId, cursorId, limit).executeAsList()
        }

    fun isBlockedEitherWay(a: Uuid, b: Uuid): Boolean =
        feed.isBlockedEitherWay(a, b, b, a).executeAsOne() > 0

    fun countTraits(): Long =
        traits.countTraits().executeAsOne()

    fun insertTrait(parentId: Uuid?, slug: String, label: String, sortOrder: Long): Traits =
        traits.insertTrait(parentId, slug, label, sortOrder).executeAsOne()

    fun getTraitById(id: Uuid): Traits? =
        traits.getTraitById(id).executeAsOneOrNull()

    fun listAllTraits(): List<Traits> =
        traits.listAllTraits().executeAsList()
}
