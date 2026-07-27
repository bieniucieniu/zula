package com.zula.features.user

import com.zula.core.http.HttpException
import com.zula.features.user.domain.UpdateMyProfileRequest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfileLogicTest {
    @Test
    fun `admin allowlist matches google subject`() {
        val config = UserAdminConfig(googleSubjectIds = setOf("google-sub-1"))
        assertTrue(config.isAdminGoogleSubject("google-sub-1"))
        assertFalse(config.isAdminGoogleSubject("other"))
        assertFalse(config.isAdminGoogleSubject(null))
        assertFalse(UserAdminConfig.fromEnv(null).isAdminGoogleSubject("google-sub-1"))
        assertTrue(UserAdminConfig.fromEnv("a, b").isAdminGoogleSubject("b"))
    }

    @Test
    fun `profile validation rejects bad fields`() {
        assertFailsWith<HttpException.BadRequest> {
            ProfileValidation.validate(UpdateMyProfileRequest(displayName = " "))
        }
        assertFailsWith<HttpException.BadRequest> {
            ProfileValidation.validate(UpdateMyProfileRequest(bio = "x".repeat(2001)))
        }
        assertFailsWith<HttpException.BadRequest> {
            ProfileValidation.validate(UpdateMyProfileRequest(timezone = "Not/AZone"))
        }
        assertFailsWith<HttpException.BadRequest> {
            ProfileValidation.validate(UpdateMyProfileRequest(preferredLanguage = "eng"))
        }
        ProfileValidation.validate(
            UpdateMyProfileRequest(
                displayName = "Ada",
                bio = "hi",
                timezone = "Europe/Warsaw",
                preferredLanguage = "pl",
            ),
        )
    }
}
