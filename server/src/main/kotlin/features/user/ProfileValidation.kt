package com.zula.features.user

import com.zula.core.http.badRequest
import com.zula.features.user.domain.UpdateMyProfileRequest
import java.time.ZoneId

object ProfileValidation {
    private val preferredLanguage = Regex("^[a-z]{2}$")

    fun validate(req: UpdateMyProfileRequest) {
        req.displayName?.let {
            val trimmed = it.trim()
            if (trimmed.isEmpty() || trimmed.length > 100) {
                badRequest("displayName must be 1–100 characters")
            }
        }
        req.bio?.let {
            PortfolioValidation.validateMarkdownLength(it, PortfolioValidation.BIO_MAX_BYTES)
        }
        req.sellerHeadline?.let {
            if (it.trim().length > 160) badRequest("sellerHeadline must be at most 160 characters")
        }
        req.locationTag?.let {
            if (it.trim().length > 100) badRequest("locationTag must be at most 100 characters")
        }
        req.avatarUrl?.let {
            val value = it.trim()
            if (value.isEmpty() || value.length > 2048) {
                badRequest("avatarUrl invalid")
            }
        }
        req.timezone?.let {
            val value = it.trim()
            runCatching { ZoneId.of(value) }.getOrElse {
                badRequest("timezone must be a valid IANA zone")
            }
        }
        req.preferredLanguage?.let {
            val value = it.trim().lowercase()
            if (!preferredLanguage.matches(value)) {
                badRequest("preferredLanguage must be ISO 639-1")
            }
        }
    }
}
