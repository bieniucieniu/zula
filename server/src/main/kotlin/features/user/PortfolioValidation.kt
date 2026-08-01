package com.zula.features.user

import com.zula.core.http.badRequest
import com.zula.features.user.domain.UpsertPortfolioItemRequest
import java.net.URI

object PortfolioValidation {
    const val BIO_MAX_BYTES = 32 * 1024
    const val CASE_STUDY_MAX_BYTES = 32 * 1024
    const val MAX_PINS = 6
    const val TITLE_MAX = 200

    private val allowedKinds = setOf("creation", "offer", "case_study", "external_link")
    private val allowedVisibility = setOf("public", "unlisted")

    fun validateMarkdownLength(source: String, maxBytes: Int = BIO_MAX_BYTES) {
        val bytes = source.toByteArray(Charsets.UTF_8).size
        if (bytes > maxBytes) {
            badRequest("markdown must be at most $maxBytes bytes")
        }
    }

    fun validateUpsert(req: UpsertPortfolioItemRequest) {
        val kind = req.kind.trim()
        if (kind !in allowedKinds) {
            badRequest("kind must be one of ${allowedKinds.joinToString()}")
        }
        if (kind == "offer") {
            badRequest("offer portfolio items require feed module")
        }
        val title = req.title.trim()
        if (title.isEmpty() || title.length > TITLE_MAX) {
            badRequest("title must be 1–$TITLE_MAX characters")
        }
        val visibility = req.visibility.trim()
        if (visibility !in allowedVisibility) {
            badRequest("visibility must be public or unlisted")
        }
        when (kind) {
            "external_link" -> {
                val url = req.externalUrl?.trim().orEmpty()
                if (url.isEmpty()) badRequest("externalUrl required for external_link")
                validateHttpsUrl(url)
            }
            "case_study" -> {
                val hasBody = !req.bodyMarkdown.isNullOrBlank()
                val hasSummary = !req.summary.isNullOrBlank()
                if (!hasBody && !hasSummary) {
                    badRequest("case_study requires bodyMarkdown or summary")
                }
                req.bodyMarkdown?.let { validateMarkdownLength(it, CASE_STUDY_MAX_BYTES) }
            }
            "creation" -> {
                req.bodyMarkdown?.let { validateMarkdownLength(it, CASE_STUDY_MAX_BYTES) }
            }
        }
        if (req.feedItemId != null) {
            badRequest("feedItemId not supported until feed module ships")
        }
    }

    fun validateHttpsUrl(url: String) {
        val uri = runCatching { URI(url) }.getOrElse {
            badRequest("externalUrl must be a valid https URL")
        }
        if (!uri.isAbsolute || uri.scheme != "https" || uri.host.isNullOrBlank()) {
            badRequest("externalUrl must be a valid https URL")
        }
    }
}
