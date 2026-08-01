package com.zula.features.groups

import com.zula.Database
import com.zula.Group_members
import com.zula.Groups
import kotlin.uuid.Uuid

class GroupRepository(
    private val database: Database,
) {
    private val queries inline get() = database.groupsQueries

    fun <T> transaction(block: GroupRepository.() -> T): T =
        database.transactionWithResult { this@GroupRepository.block() }

    fun insertGroup(
        slug: String,
        title: String,
        description: String?,
        visibility: String,
        createdBy: Uuid,
        createdAt: Long,
    ): Groups =
        queries.insertGroup(slug, title, description, visibility, createdBy, createdAt).executeAsOne()

    fun getGroupById(id: Uuid): Groups? =
        queries.getGroupById(id).executeAsOneOrNull()

    fun getGroupBySlug(slug: String): Groups? =
        queries.getGroupBySlug(slug).executeAsOneOrNull()

    fun updateGroup(
        id: Uuid,
        title: String,
        description: String?,
        visibility: String,
    ): Groups? =
        queries.updateGroup(title, description, visibility, id).executeAsOneOrNull()

    fun insertMember(groupId: Uuid, userId: Uuid, role: String, joinedAt: Long) {
        queries.insertMember(groupId, userId, role, joinedAt)
    }

    fun deleteMember(groupId: Uuid, userId: Uuid) {
        queries.deleteMember(groupId, userId)
    }

    fun getMember(groupId: Uuid, userId: Uuid): Group_members? =
        queries.getMember(groupId, userId).executeAsOneOrNull()

    fun listMembers(groupId: Uuid, cursorUserId: Uuid?, limit: Long): List<Group_members> =
        if (cursorUserId == null) {
            queries.listMembersFirst(groupId, limit).executeAsList()
        } else {
            queries.listMembersPage(groupId, cursorUserId, limit).executeAsList()
        }
}
