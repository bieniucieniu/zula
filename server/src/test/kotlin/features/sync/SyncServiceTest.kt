package com.zula.features.sync

import com.zula.core.http.forbidden
import com.zula.features.sync.domain.SyncBatchRequest
import com.zula.features.sync.domain.SyncOp
import com.zula.features.sync.domain.SyncOpType
import com.zula.features.user.ProfileWrite
import com.zula.features.user.UserProfileWriter
import com.zula.lib.id.Ids
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        val result = response.results.single()
        assertFalse(result.ok)
        val problem = assertNotNull(result.problem)
        assertEquals(HttpStatusCode.BadRequest.value, problem.status)
        assertEquals("Bad Request", problem.title)
        assertTrue(problem.detail!!.contains("unsupported"))
        assertEquals("/api/sync/batch#op/1:unknown_table/${actor}", problem.instance)
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
        val result = response.results.single()
        assertTrue(result.ok)
        assertNull(result.problem)
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
        val problem = assertNotNull(response.results.single().problem)
        assertEquals(400, problem.status)
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
        assertNotNull(response.results.single().problem)
    }

    @Test
    fun `client HttpException from writer becomes per-op problem details`() {
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
        val problem = assertNotNull(result.problem)
        assertEquals(403, problem.status)
        assertEquals("Forbidden", problem.title)
        assertEquals("Can only modify own profile", problem.detail)
        assertEquals("/api/sync/batch#op/3:user_profiles/${actor}", problem.instance)
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
