package com.zula.features.groups

import com.zula.Groups
import com.zula.core.http.badRequest
import com.zula.core.http.conflict
import com.zula.core.http.forbidden
import com.zula.core.http.notFound
import com.zula.features.groups.domain.CreateGroupRequest
import com.zula.features.groups.domain.GroupCursor
import com.zula.features.groups.domain.GroupMemberResponse
import com.zula.features.groups.domain.GroupResponse
import com.zula.features.groups.domain.LeaveGroupResponse
import com.zula.features.groups.domain.ListGroupMembersResponse
import com.zula.features.groups.domain.UpdateGroupRequest
import com.zula.lib.id.Ids
import java.time.Instant
import kotlin.uuid.Uuid

class GroupService(
    private val repository: GroupRepository,
) {
    fun create(ownerId: Uuid, req: CreateGroupRequest): GroupResponse {
        val slug = req.slug.trim().lowercase()
        if (!SLUG_REGEX.matches(slug)) badRequest("slug must be 2–64 chars [a-z0-9-]")
        val title = req.title.trim()
        if (title.isEmpty() || title.length > 120) badRequest("title must be 1–120 characters")
        val visibility = req.visibility.trim().lowercase()
        if (visibility !in VISIBILITIES) badRequest("visibility must be public or private")
        if (repository.getGroupBySlug(slug) != null) conflict("slug already taken")
        val now = Instant.now().epochSecond
        val group = repository.transaction {
            val created = insertGroup(
                slug = slug,
                title = title,
                description = req.description?.trim()?.ifEmpty { null },
                visibility = visibility,
                createdBy = ownerId,
                createdAt = now,
            )
            insertMember(created.id, ownerId, "owner", now)
            created
        }
        return group.toResponse(viewerRole = "owner")
    }

    fun get(id: String, viewerId: Uuid?): GroupResponse {
        val group = resolveGroup(id)
        val member = viewerId?.let { repository.getMember(group.id, it) }
        if (group.visibility == "private" && member == null) {
            notFound("Group not found")
        }
        return group.toResponse(viewerRole = member?.role)
    }

    fun patch(actorId: Uuid, groupId: Uuid, req: UpdateGroupRequest): GroupResponse {
        val group = repository.getGroupById(groupId) ?: notFound("Group not found")
        val member = repository.getMember(groupId, actorId)
            ?: forbidden("Not a group member")
        if (member.role != "owner" && member.role != "admin") {
            forbidden("Owner or admin required")
        }
        val title = req.title?.trim() ?: group.title
        if (title.isEmpty() || title.length > 120) badRequest("title must be 1–120 characters")
        val description = when {
            req.description == null -> group.description
            else -> req.description.trim().ifEmpty { null }
        }
        val visibility = req.visibility?.trim()?.lowercase() ?: group.visibility
        if (visibility !in VISIBILITIES) badRequest("visibility must be public or private")
        val updated = repository.updateGroup(groupId, title, description, visibility)
            ?: notFound("Group not found")
        return updated.toResponse(viewerRole = member.role)
    }

    fun join(userId: Uuid, groupId: Uuid): GroupResponse {
        val group = repository.getGroupById(groupId) ?: notFound("Group not found")
        if (group.visibility != "public") forbidden("Cannot join private group")
        val existing = repository.getMember(groupId, userId)
        if (existing != null) return group.toResponse(viewerRole = existing.role)
        val now = Instant.now().epochSecond
        repository.insertMember(groupId, userId, "member", now)
        return group.toResponse(viewerRole = "member")
    }

    fun leave(userId: Uuid, groupId: Uuid): LeaveGroupResponse {
        val member = repository.getMember(groupId, userId) ?: notFound("Membership not found")
        if (member.role == "owner") forbidden("Owner cannot leave group")
        repository.deleteMember(groupId, userId)
        return LeaveGroupResponse(groupId = groupId.toString())
    }

    fun listMembers(
        actorId: Uuid,
        groupId: Uuid,
        cursorUserId: Uuid?,
        limit: Int,
    ): ListGroupMembersResponse {
        repository.getGroupById(groupId) ?: notFound("Group not found")
        repository.getMember(groupId, actorId) ?: forbidden("Not a group member")
        val pageSize = limit.coerceIn(1, 50)
        val rows = repository.listMembers(groupId, cursorUserId, (pageSize + 1).toLong())
        val hasMore = rows.size > pageSize
        val page = if (hasMore) rows.take(pageSize) else rows
        val members = page.map {
            GroupMemberResponse(
                groupId = it.group_id.toString(),
                userId = it.user_id.toString(),
                role = it.role,
                joinedAt = it.joined_at,
            )
        }
        return ListGroupMembersResponse(
            members = members,
            nextCursor = members.lastOrNull()?.takeIf { hasMore }?.let { GroupCursor(it.userId) },
            hasMore = hasMore,
        )
    }

    fun requireMember(groupId: Uuid, userId: Uuid) {
        repository.getMember(groupId, userId) ?: forbidden("Not a group member")
    }

    fun isMember(groupId: Uuid, userId: Uuid): Boolean =
        repository.getMember(groupId, userId) != null

    fun getGroupOrNotFound(groupId: Uuid): Groups =
        repository.getGroupById(groupId) ?: notFound("Group not found")

    fun resolveGroup(id: String): Groups {
        val byId = Ids.parseOrNull(id)?.let { repository.getGroupById(it) }
        if (byId != null) return byId
        return repository.getGroupBySlug(id) ?: notFound("Group not found")
    }

    private fun Groups.toResponse(viewerRole: String?) = GroupResponse(
        id = id.toString(),
        slug = slug,
        title = title,
        description = description,
        visibility = visibility,
        createdBy = created_by.toString(),
        createdAt = created_at,
        viewerRole = viewerRole,
    )

    companion object {
        private val VISIBILITIES = setOf("public", "private")
        private val SLUG_REGEX = Regex("^[a-z0-9][a-z0-9-]{1,63}$")
    }
}
