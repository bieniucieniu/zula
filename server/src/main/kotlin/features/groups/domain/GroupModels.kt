package com.zula.features.groups.domain

import kotlinx.serialization.Serializable

@Serializable
data class GroupCursor(
    val id: String,
)

@Serializable
data class CreateGroupRequest(
    val slug: String,
    val title: String,
    val description: String? = null,
    val visibility: String = "public",
)

@Serializable
data class UpdateGroupRequest(
    val title: String? = null,
    val description: String? = null,
    val visibility: String? = null,
)

@Serializable
data class GroupResponse(
    val id: String,
    val slug: String,
    val title: String,
    val description: String? = null,
    val visibility: String,
    val createdBy: String,
    val createdAt: Long,
    val viewerRole: String? = null,
)

@Serializable
data class GroupMemberResponse(
    val groupId: String,
    val userId: String,
    val role: String,
    val joinedAt: Long,
)

@Serializable
data class ListGroupMembersResponse(
    val members: List<GroupMemberResponse>,
    val nextCursor: GroupCursor? = null,
    val hasMore: Boolean,
)

@Serializable
data class LeaveGroupResponse(
    val groupId: String,
)
