package com.zula.features.chat

import com.zula.Sse_event_log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

data class SseLiveEvent(
    val id: Uuid,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
)

/** Fan-out sink used by trade/chat to push events to a user. */
fun interface SseEventSink {
    fun emit(userId: Uuid, type: String, payloadJson: String)
}

/**
 * In-memory fan-out + durable [sse_event_log] for Last-Event-ID replay.
 */
class SseEventHub(
    private val repository: ChatRepository,
) : SseEventSink {
    private val flows = ConcurrentHashMap<Uuid, MutableSharedFlow<SseLiveEvent>>()

    override fun emit(userId: Uuid, type: String, payloadJson: String) {
        val now = Instant.now().epochSecond
        val row = repository.insertSseEvent(userId, type, payloadJson, now)
        val event = row.toLive()
        flowFor(userId).tryEmit(event)
    }

    fun subscribe(userId: Uuid): Flow<SseLiveEvent> =
        flowFor(userId).asSharedFlow()

    fun replayAfter(userId: Uuid, afterId: Uuid?, limit: Int = 200): List<SseLiveEvent> {
        val rows = if (afterId == null) {
            emptyList()
        } else {
            repository.listSseEventsAfter(userId, afterId, limit.toLong())
        }
        return rows.map { it.toLive() }
    }

    private fun flowFor(userId: Uuid): MutableSharedFlow<SseLiveEvent> =
        flows.computeIfAbsent(userId) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 64,
            )
        }

    private fun Sse_event_log.toLive() = SseLiveEvent(
        id = id,
        type = event_type,
        payloadJson = payload,
        createdAt = created_at,
    )
}
