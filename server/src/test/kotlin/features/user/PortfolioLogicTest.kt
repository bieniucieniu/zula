package com.zula.features.user

import com.zula.core.http.HttpException
import com.zula.features.user.domain.UpsertPortfolioItemRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class PortfolioLogicTest {
    @Test
    fun `bio markdown length reject`() {
        assertFailsWith<HttpException.BadRequest> {
            PortfolioValidation.validateMarkdownLength("x".repeat(PortfolioValidation.BIO_MAX_BYTES + 1))
        }
        PortfolioValidation.validateMarkdownLength("ok")
    }

    @Test
    fun `reject offer kind until feed exists`() {
        assertFailsWith<HttpException.BadRequest> {
            PortfolioValidation.validateUpsert(
                UpsertPortfolioItemRequest(kind = "offer", title = "Listing"),
            )
        }
    }

    @Test
    fun `reject feedItemId until feed exists`() {
        assertFailsWith<HttpException.BadRequest> {
            PortfolioValidation.validateUpsert(
                UpsertPortfolioItemRequest(
                    kind = "creation",
                    title = "Work",
                    feedItemId = Uuid.random().toString(),
                ),
            )
        }
    }

    @Test
    fun `external_link requires https`() {
        assertFailsWith<HttpException.BadRequest> {
            PortfolioValidation.validateUpsert(
                UpsertPortfolioItemRequest(kind = "external_link", title = "Site"),
            )
        }
        assertFailsWith<HttpException.BadRequest> {
            PortfolioValidation.validateUpsert(
                UpsertPortfolioItemRequest(
                    kind = "external_link",
                    title = "Site",
                    externalUrl = "http://example.com",
                ),
            )
        }
        PortfolioValidation.validateUpsert(
            UpsertPortfolioItemRequest(
                kind = "external_link",
                title = "Site",
                externalUrl = "https://example.com/me",
            ),
        )
    }

    @Test
    fun `case_study requires body or summary`() {
        assertFailsWith<HttpException.BadRequest> {
            PortfolioValidation.validateUpsert(
                UpsertPortfolioItemRequest(kind = "case_study", title = "Deal"),
            )
        }
        PortfolioValidation.validateUpsert(
            UpsertPortfolioItemRequest(kind = "case_study", title = "Deal", summary = "Shipped"),
        )
    }

    @Test
    fun `title length and visibility`() {
        assertFailsWith<HttpException.BadRequest> {
            PortfolioValidation.validateUpsert(
                UpsertPortfolioItemRequest(kind = "creation", title = ""),
            )
        }
        assertFailsWith<HttpException.BadRequest> {
            PortfolioValidation.validateUpsert(
                UpsertPortfolioItemRequest(kind = "creation", title = "x".repeat(201)),
            )
        }
        assertFailsWith<HttpException.BadRequest> {
            PortfolioValidation.validateUpsert(
                UpsertPortfolioItemRequest(kind = "creation", title = "Art", visibility = "secret"),
            )
        }
    }

    @Test
    fun `pin max constant is six`() {
        assertEquals(6, PortfolioValidation.MAX_PINS)
    }
}
