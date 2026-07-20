package com.zula.features.sync

import com.zula.core.http.forbidden
import com.zula.features.sync.domain.SyncBatchRequest
import com.zula.features.sync.domain.SyncOp
import com.zula.features.sync.domain.SyncOpType
import com.zula.features.user.ProfileWrite
import com.zula.features.user.UserProfileWriter
import com.zula.lib.id.Ids
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SyncServiceTest {
    @Test
    fun `toProfileWrite maps snake_case columns`() {
        val json = buildJsonObject {
            put("display_name", "Ada")
            put("bio", "hi")
            put("seller_headline", "seller")
        }
        val write = json.toProfileWrite()
        assertEquals("Ada", write.displayName)
        assertEquals("hi", write.bio)
        assertEquals("seller", write.sellerHeadline)
        assertEquals(null, write.avatarUrl)
    }

    @Test
    fun `unsupported table is rejected without throwing`() {
        val actor = Ids.next()
        val service = SyncService(FakeProfiles())
        val response = service.applyBatch(
            actor,
            SyncBatchRequest(
                ops = listOf(
                    SyncOp(
                        clientId = 1,
                        op = SyncOpType.PATCH,
                        table = "unknown_table",
                        id = actor.toString(),
                        opData = buildJsonObject { put("x", "y") },
                    ),
                ),
            ),
        )
        assertEquals(1, response.results.size)
        assertFalse(response.results.single().ok)
        assertTrue(response.results.single().error!!.contains("unsupported"))
    }

    @Test
    fun `user_profiles patch dispatches to UserProfileWriter`() {
        val actor = Ids.next()
        val profiles = FakeProfiles()
        val service = SyncService(profiles)
        val response = service.applyBatch(
            actor,
            SyncBatchRequest(
                ops = listOf(
                    SyncOp(
                        clientId = 7,
                        op = SyncOpType.PATCH,
                        table = "user_profiles",
                        id = actor.toString(),
                        opData = buildJsonObject { put("display_name", "Neo") },
                    ),
                ),
            ),
        )
        assertTrue(response.results.single().ok)
        assertEquals(1, profiles.patches.size)
        assertEquals(actor, profiles.patches.single().actor)
        assertEquals("Neo", profiles.patches.single().write.displayName)
    }

    @Test
    fun `users table writes are rejected`() {
        val actor = Ids.next()
        val service = SyncService(FakeProfiles())
        val response = service.applyBatch(
            actor,
            SyncBatchRequest(
                ops = listOf(
                    SyncOp(
                        op = SyncOpType.PATCH,
                        table = "users",
                        id = actor.toString(),
                        opData = buildJsonObject { put("username", "x") },
                    ),
                ),
            ),
        )
        assertFalse(response.results.single().ok)
    }

    @Test
    fun `profile delete is rejected`() {
        val actor = Ids.next()
        val service = SyncService(FakeProfiles())
        val response = service.applyBatch(
            actor,
            SyncBatchRequest(
                ops = listOf(
                    SyncOp(
                        op = SyncOpType.DELETE,
                        table = "user_profiles",
                        id = actor.toString(),
                    ),
                ),
            ),
        )
        assertFalse(response.results.single().ok)
    }

    @Test
    fun `client HttpException from writer becomes per-op failure`() {
        val actor = Ids.next()
        val service = SyncService(
            object : UserProfileWriter {
                override fun putMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) =
                    forbidden("Can only modify own profile")

                override fun patchMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) =
                    forbidden("Can only modify own profile")
            },
        )
        val response = service.applyBatch(
            actor,
            SyncBatchRequest(
                ops = listOf(
                    SyncOp(
                        clientId = 3,
                        op = SyncOpType.PATCH,
                        table = "user_profiles",
                        id = actor.toString(),
                        opData = buildJsonObject { put("display_name", "X") },
                    ),
                ),
            ),
        )
        val result = response.results.single()
        assertFalse(result.ok)
        assertEquals("Can only modify own profile", result.error)
        assertFalse(result.retryable)
    }

    @Test
    fun `unexpected throwable from writer is not swallowed`() {
        val actor = Ids.next()
        val service = SyncService(
            object : UserProfileWriter {
                override fun putMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) {
                    error("db down")
                }

                override fun patchMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) {
                    error("db down")
                }
            },
        )
        assertFailsWith<IllegalStateException> {
            service.applyBatch(
                actor,
                SyncBatchRequest(
                    ops = listOf(
                        SyncOp(
                            op = SyncOpType.PATCH,
                            table = "user_profiles",
                            id = actor.toString(),
                        ),
                    ),
                ),
            )
        }
    }
}

private data class PatchRec(val actor: Uuid, val profile: Uuid, val write: ProfileWrite)

private class FakeProfiles : UserProfileWriter {
    val patches = mutableListOf<PatchRec>()
    val puts = mutableListOf<PatchRec>()

    override fun putMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) {
        puts += PatchRec(actorId, profileId, write)
    }

    override fun patchMyProfile(actorId: Uuid, profileId: Uuid, write: ProfileWrite) {
        patches += PatchRec(actorId, profileId, write)
    }
}
