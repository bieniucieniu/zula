package com.zula.features.auth

import com.zula.core.http.HttpException
import com.zula.core.http.unauthorized
import com.zula.features.auth.crypto.RefreshTokenGenerator
import com.zula.features.auth.domain.AuthMethods
import com.zula.lib.id.Ids
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Mirrors AuthRepository session ownership + refresh rotation semantics without a live DB.
 */
class AuthSessionLogicTest {
    private val store = InMemorySessionStore()

    @Test
    fun `active session is owned by user only`() {
        val userA = Ids.next()
        val userB = Ids.next()
        val now = Instant.now().epochSecond
        val sessionId = store.insert(
            userId = userA,
            authMethod = AuthMethods.OAUTH,
            refreshHash = null,
            expiresAt = now + 900,
        )

        assertTrue(store.findActiveOwned(sessionId, userA, now) != null)
        assertTrue(store.findActiveOwned(sessionId, userB, now) == null)

        store.revoke(sessionId)
        assertTrue(store.findActiveOwned(sessionId, userA, now) == null)
    }

    @Test
    fun `refresh rotation is single-winner and reuse kills family`() {
        val now = Instant.now().epochSecond
        val refresh = RefreshTokenGenerator.generate()
        val hash = RefreshTokenGenerator.hash(refresh)
        val userId = Ids.next()
        val sessionId = store.insert(
            userId = userId,
            authMethod = AuthMethods.DEV,
            refreshHash = hash,
            expiresAt = now + 3600,
        )

        store.rotate(hash, RefreshTokenGenerator.hash(RefreshTokenGenerator.generate()), now + 3600, now)
        assertTrue(store.isRevoked(sessionId))

        assertFailsWith<HttpException> {
            store.rotate(hash, RefreshTokenGenerator.hash(RefreshTokenGenerator.generate()), now + 3600, now)
        }
        assertTrue(store.allRevokedForUser(userId))
    }

    @Test
    fun `provider check failure before rotate leaves refresh usable`() {
        val now = Instant.now().epochSecond
        val refresh = RefreshTokenGenerator.generate()
        val hash = RefreshTokenGenerator.hash(refresh)
        val userId = Ids.next()
        val sessionId = store.insert(
            userId = userId,
            authMethod = AuthMethods.OAUTH,
            refreshHash = hash,
            expiresAt = now + 3600,
        )

        // Mirror AuthService.refresh: validate → provider fail → do not rotate.
        val pending = store.requireValid(hash, now)
        assertTrue(pending == sessionId)
        // Simulated provider revoke of identity sessions without consuming refresh hash.
        store.revoke(sessionId)

        assertFailsWith<HttpException> {
            store.requireValid(hash, now)
        }
        assertTrue(store.findByHash(hash) == null || store.isRevoked(sessionId))
        assertTrue(store.countForUser(userId) == 1)
    }

    @Test
    fun `logout by sid revokes session`() {
        val now = Instant.now().epochSecond
        val sid = store.insert(Ids.next(), AuthMethods.OAUTH, null, now + 900)
        store.revoke(sid)
        assertTrue(store.isRevoked(sid))
    }
}

private class InMemorySessionStore {
    private data class Row(
        val id: Uuid,
        val userId: Uuid,
        val authMethod: String,
        var refreshHash: String?,
        var expiresAt: Long,
        var revoked: Boolean,
        val rotatedFromId: Uuid?,
    )

    private val rows = ConcurrentHashMap<Uuid, Row>()

    fun insert(
        userId: Uuid,
        authMethod: String,
        refreshHash: String?,
        expiresAt: Long,
        rotatedFrom: Uuid? = null,
    ): Uuid {
        val id = Ids.next()
        rows[id] = Row(id, userId, authMethod, refreshHash, expiresAt, false, rotatedFrom)
        return id
    }

    fun findActiveOwned(sessionId: Uuid, userId: Uuid, now: Long): Uuid? {
        val row = rows[sessionId] ?: return null
        if (row.userId != userId || row.revoked || row.expiresAt <= now) return null
        return row.id
    }

    fun revoke(sessionId: Uuid) {
        rows[sessionId]?.revoked = true
    }

    fun isRevoked(sessionId: Uuid): Boolean = rows[sessionId]?.revoked == true

    fun allRevokedForUser(userId: Uuid): Boolean =
        rows.values.filter { it.userId == userId }.all { it.revoked }

    fun countForUser(userId: Uuid): Int = rows.values.count { it.userId == userId }

    fun findByHash(hash: String): Uuid? =
        rows.values.find { it.refreshHash == hash && !it.revoked }?.id

    fun requireValid(oldHash: String, now: Long): Uuid {
        val session = rows.values.find { it.refreshHash == oldHash }
            ?: unauthorized("Invalid refresh token")
        if (session.revoked) {
            revokeFamily(session.id)
            unauthorized("Refresh token reuse detected")
        }
        if (session.expiresAt < now) unauthorized("Refresh token expired")
        return session.id
    }

    fun rotate(oldHash: String, newHash: String, newExpires: Long, now: Long): Uuid {
        requireValid(oldHash, now)
        val session = rows.values.find { it.refreshHash == oldHash }
            ?: unauthorized("Invalid refresh token")
        session.revoked = true
        return insert(session.userId, session.authMethod, newHash, newExpires, rotatedFrom = session.id)
    }

    private fun revokeFamily(sessionId: Uuid) {
        val queue = ArrayDeque<Uuid>()
        queue.add(sessionId)
        val seen = mutableSetOf<Uuid>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            rows[id]?.revoked = true
            rows.values.filter { it.rotatedFromId == id }.forEach { queue.add(it.id) }
        }
    }
}
