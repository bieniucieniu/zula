package com.zula.features.auth.provider

import com.zula.core.http.HttpException
import com.zula.features.auth.domain.AuthCredential
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DevAuthProviderTest {
  private val provider = DevAuthProvider(
    secret = "local-dev-bypass",
    defaultEmail = "dev@zula.local",
  )

  @Test
  fun `accepts shared secret and default email`() = runTest {
    val identity = provider.verify(
      AuthCredential.DevBypass(
        secret = "local-dev-bypass",
        email = null,
      ),
    )

    assertEquals("dev", identity.provider)
    assertEquals("dev@zula.local", identity.email)
    assertEquals("dev@zula.local", identity.providerUserId)
  }

  @Test
  fun `accepts custom email via deviceInfo`() = runTest {
    val identity = provider.verify(
      AuthCredential.DevBypass(
        secret = "local-dev-bypass",
        email = "native-tester@zula.local",
      ),
    )

    assertEquals("native-tester@zula.local", identity.email)
    assertEquals("native-tester@zula.local", identity.providerUserId)
  }

  @Test
  fun `rejects wrong secret`() = runTest {
    assertFailsWith<HttpException> {
      provider.verify(
        AuthCredential.DevBypass(
          secret = "wrong",
          email = null,
        ),
      )
    }
  }
}
