package com.zula.features.auth.provider

import com.zula.core.http.badRequest
import com.zula.features.auth.domain.AuthCredential
import com.zula.core.http.unauthorized
import com.zula.features.auth.domain.Identity
import com.zula.features.auth.persistence.AuthRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

class EmailOtpAuthProvider(
    private val repository: AuthRepository,
) : AuthProvider {
    override val id: String = "email_otp"

    override fun info() = null

    override suspend fun verify(credential: AuthCredential): Identity {
        val otp = credential as? AuthCredential.EmailOtp
            ?: badRequest("email_otp requires EmailOtp credential")
        val challenge = repository.findChallenge(otp.challengeId)
            ?: unauthorized("Challenge not found")
        if (challenge.consumed_at != null) unauthorized("Challenge already used")
        if (challenge.expires_at < Instant.now().epochSecond) unauthorized("Challenge expired")
        if (challenge.attempts >= MAX_ATTEMPTS) unauthorized("Too many attempts")

        repository.incrementChallengeAttempts(otp.challengeId)
        val hash = hashCode(challenge.target, otp.code)
        if (hash != challenge.code_hash) unauthorized("Invalid code")

        if (!repository.consumeChallenge(otp.challengeId)) {
            unauthorized("Challenge already used")
        }
        return Identity(
            provider = id,
            providerUserId = challenge.target.lowercase(),
            email = if (challenge.channel == "email") challenge.target else null,
        )
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        private val secureRandom = SecureRandom()

        fun hashCode(target: String, code: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest("$target:$code".toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun generateCode(): String {
            val n = secureRandom.nextInt(900_000) + 100_000
            return n.toString()
        }
    }
}
