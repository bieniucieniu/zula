package com.zula.features.auth.persistence

import com.zula.*
import com.zula.core.database.insertWithOptimisticId
import com.zula.core.http.badRequest
import com.zula.features.auth.domain.AuthMethods
import com.zula.core.http.unauthorized
import java.time.Instant
import kotlin.uuid.Uuid

class AuthRepository(
    private val database: Database,
) {
    private val queries inline get() = database.userQueries

    fun findUserByIdentity(provider: String, providerUserId: String): Users? =
        queries.findUserByIdentity(provider, providerUserId).executeAsOneOrNull()

    fun findUserByUsername(username: String): Users? =
        queries.findUserByUsername(username).executeAsOneOrNull()

    fun createUser(
        preferredId: Uuid?,
        username: String,
        now: Long = System.currentTimeMillis() / 1000,
    ): Uuid =
        database.transactionWithResult {
            val id = insertWithOptimisticId(
                preferredId = preferredId,
                insertWithId = { userId ->
                    queries.insertUser(userId, username)
                    queries.insertUserProfile(userId, username, now)
                    queries.upsertUserStats(userId, now)
                    userId
                },
                insertAuto = {
                    val userId = queries.insertUserAuto(username).executeAsOne()
                    queries.insertUserProfile(userId, username, now)
                    queries.upsertUserStats(userId, now)
                    userId
                },
            )
            id
        }

    fun insertIdentity(
        preferredId: Uuid?,
        userId: Uuid,
        provider: String,
        providerUserId: String,
        email: String?,
        metadata: String?,
        refreshEnc: String?,
        status: String,
        scopes: String?,
    ): Uuid =
        insertWithOptimisticId(
            preferredId = preferredId,
            insertWithId = { identityId ->
                queries.insertIdentity(
                    id = identityId,
                    user_id = userId,
                    provider = provider,
                    provider_user_id = providerUserId,
                    email = email,
                    provider_metadata = metadata,
                    provider_refresh_token_enc = refreshEnc,
                    credentials_status = status,
                    scopes = scopes,
                )
                identityId
            },
            insertAuto = {
                queries.insertIdentityAuto(
                    user_id = userId,
                    provider = provider,
                    provider_user_id = providerUserId,
                    email = email,
                    provider_metadata = metadata,
                    provider_refresh_token_enc = refreshEnc,
                    credentials_status = status,
                    scopes = scopes,
                ).executeAsOne()
            },
        )

    fun findIdentity(provider: String, providerUserId: String): User_identities? =
        queries.findIdentityByProvider(provider, providerUserId).executeAsOneOrNull()

    fun updateIdentityLogin(id: Uuid, email: String?, metadata: String?, status: String) {
        queries.updateIdentityLogin(email, metadata, status, id)
    }

    fun updateIdentityProviderRefresh(id: Uuid, refreshEnc: String?, status: String, checkedAt: Long) {
        queries.updateIdentityProviderRefresh(refreshEnc, status, checkedAt, id)
    }

    fun updateIdentityProviderTokens(
        id: Uuid,
        accessEnc: String?,
        expiresAt: Long?,
        status: String,
        checkedAt: Long,
    ) {
        queries.updateIdentityProviderTokens(accessEnc, expiresAt, status, checkedAt, id)
    }

    fun revokeIdentityCredentials(id: Uuid, revokedAt: Long = System.currentTimeMillis() / 1000) {
        queries.revokeIdentityCredentials(revokedAt, id)
    }

    fun findIdentityByUserAndProvider(userId: Uuid, provider: String): User_identities? =
        queries.findIdentityByUserAndProvider(userId, provider).executeAsOneOrNull()

    fun listIdentitiesByUser(userId: Uuid): List<User_identities> =
        queries.listIdentitiesByUser(userId).executeAsList()

    fun insertSession(
        preferredId: Uuid?,
        userId: Uuid,
        authMethod: String,
        refreshHash: String?,
        deviceInfo: String?,
        ipAddress: String?,
        expiresAt: Long,
        rotatedFromId: Uuid?,
    ): Uuid =
        insertWithOptimisticId(
            preferredId = preferredId,
            insertWithId = { sessionId ->
                queries.insertSession(
                    id = sessionId,
                    user_id = userId,
                    auth_method = authMethod,
                    refresh_token_hash = refreshHash,
                    device_info = deviceInfo,
                    ip_address = ipAddress,
                    expires_at = expiresAt,
                    rotated_from_id = rotatedFromId,
                )
                sessionId
            },
            insertAuto = {
                queries.insertSessionAuto(
                    user_id = userId,
                    auth_method = authMethod,
                    refresh_token_hash = refreshHash,
                    device_info = deviceInfo,
                    ip_address = ipAddress,
                    expires_at = expiresAt,
                    rotated_from_id = rotatedFromId,
                ).executeAsOne()
            },
        )

    fun findSession(id: Uuid): User_sessions? =
        queries.findSessionById(id).executeAsOneOrNull()

    fun findActiveSessionOwnedBy(sessionId: Uuid, userId: Uuid, now: Long = Instant.now().epochSecond): User_sessions? =
        queries.findActiveSessionOwnedBy(sessionId, userId, now).executeAsOneOrNull()

    fun findSessionByRefreshHash(hash: String): User_sessions? =
        queries.findSessionByRefreshHash(hash).executeAsOneOrNull()

    fun findAnySessionByRefreshHash(hash: String): User_sessions? =
        queries.findAnySessionByRefreshHash(hash).executeAsOneOrNull()

    fun findSessionsRotatedFrom(sessionId: Uuid): List<User_sessions> =
        queries.findSessionsRotatedFrom(sessionId).executeAsList()

    fun revokeSession(id: Uuid) = queries.revokeSession(id)

    fun revokeRotationFamily(sessionId: Uuid) {
        val queue = ArrayDeque<Uuid>()
        queue.add(sessionId)
        val seen = mutableSetOf<Uuid>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            revokeSession(id)
            findSessionsRotatedFrom(id).forEach { queue.add(it.id) }
        }
    }

    fun rotateRefreshSession(
        refreshHash: String,
        newSessionId: Uuid,
        newRefreshHash: String,
        newExpiresAt: Long,
        now: Long = Instant.now().epochSecond,
    ): User_sessions {
        return database.transactionWithResult {
            val session = queries.findAnySessionByRefreshHash(refreshHash).executeAsOneOrNull()
                ?: unauthorized("Invalid refresh token")

            if (session.is_revoked != 0L) {
                revokeRotationFamily(session.id)
                unauthorized("Refresh token reuse detected")
            }
            if (session.expires_at < now) {
                unauthorized("Refresh token expired")
            }
            if (session.auth_method == AuthMethods.OAUTH) {
                badRequest(
                    "OAuth sessions must re-authenticate with provider id_token",
                )
            }

            val claimedId = queries.claimSessionForRefresh(session.id, now).executeAsOneOrNull()
            if (claimedId == null) {
                unauthorized("Invalid refresh token")
            }

            insertSession(
                preferredId = newSessionId,
                userId = session.user_id,
                authMethod = session.auth_method,
                refreshHash = newRefreshHash,
                deviceInfo = session.device_info,
                ipAddress = session.ip_address,
                expiresAt = newExpiresAt,
                rotatedFromId = session.id,
            )
            session
        }
    }

    fun extendOAuthSession(id: Uuid, userId: Uuid, expiresAt: Long) {
        queries.extendOAuthSession(expiresAt, id, userId)
    }

    fun getUserById(id: Uuid): Users? = queries.getUserById(id).executeAsOneOrNull()

    fun insertChallenge(
        preferredId: Uuid?,
        channel: String,
        target: String,
        codeHash: String,
        purpose: String,
        expiresAt: Long,
    ): Uuid =
        insertWithOptimisticId(
            preferredId = preferredId,
            insertWithId = { challengeId ->
                queries.insertAuthChallenge(challengeId, channel, target, codeHash, purpose, expiresAt)
                challengeId
            },
            insertAuto = {
                queries.insertAuthChallengeAuto(channel, target, codeHash, purpose, expiresAt).executeAsOne()
            },
        )

    fun findChallenge(id: Uuid): Auth_challenges? =
        queries.findAuthChallenge(id).executeAsOneOrNull()

    fun findChallengeByTokenHash(hash: String): Auth_challenges? =
        queries.findAuthChallengeByCodeHash(hash).executeAsOneOrNull()

    fun consumeChallenge(id: Uuid, now: Long = System.currentTimeMillis() / 1000): Boolean {
        return queries.consumeAuthChallenge(now, id).executeAsOneOrNull() != null
    }

    fun incrementChallengeAttempts(id: Uuid) = queries.incrementChallengeAttempts(id)
}
