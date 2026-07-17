package com.zula.features.auth.provider

import com.zula.core.http.badRequest
import com.zula.features.auth.domain.AuthCredential
import com.zula.core.http.unauthorized
import com.zula.features.auth.domain.Identity
import com.zula.features.auth.persistence.AuthRepository
import com.zula.lib.id.Ids
import java.security.MessageDigest
import java.time.Instant

class MagicLinkAuthProvider(
    private val repository: AuthRepository,
) : AuthProvider {
    override val id: String = "magic_link"

    override fun info() = null

    override suspend fun verify(credential: AuthCredential): Identity {
        val link = credential as? AuthCredential.MagicLink
            ?: badRequest("magic_link requires MagicLink credential")
        val challenge = repository.findChallengeByTokenHash(hashToken(link.token))
            ?: unauthorized("Invalid or expired magic link")
        if (challenge.consumed_at != null) unauthorized("Link already used")
        if (challenge.expires_at < Instant.now().epochSecond) unauthorized("Link expired")

        if (!repository.consumeChallenge(challenge.id)) {
            unauthorized("Link already used")
        }
        return Identity(
            provider = id,
            providerUserId = challenge.target.lowercase(),
            email = if (challenge.channel == "email") challenge.target else null,
        )
    }

    companion object {
        fun hashToken(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        fun generateToken(): String =
            Ids.next().toHexString() + Ids.next().toHexString()
    }
}
