import { useQueryClient } from "@tanstack/react-query"
import { useEffect, useRef } from "react"

const MESSAGE_RECEIVED = "MESSAGE_RECEIVED"
const TRADE_STATE_CHANGED = "TRADE_STATE_CHANGED"

/**
 * SSE to /api/events/stream (cookie auth). Invalidates chat/trade queries on events.
 */
export function useEventStream(enabled = true) {
  const queryClient = useQueryClient()
  const lastEventIdRef = useRef<string | null>(null)

  useEffect(() => {
    if (!enabled) return

    let cancelled = false
    let retryTimer: ReturnType<typeof setTimeout> | undefined

    async function connect() {
      if (cancelled) return

      const headers: HeadersInit = { Accept: "text/event-stream" }
      if (lastEventIdRef.current) {
        headers["Last-Event-ID"] = lastEventIdRef.current
      }

      try {
        const res = await fetch("/api/events/stream", {
          method: "GET",
          credentials: "include",
          headers,
        })
        if (!res.ok || !res.body) {
          throw new Error(`SSE failed: ${res.status}`)
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ""

        while (!cancelled) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })

          const parts = buffer.split("\n\n")
          buffer = parts.pop() ?? ""

          for (const part of parts) {
            if (!part.trim() || part.startsWith(":")) continue

            let eventType = "message"
            let data = ""
            let id: string | null = null

            for (const line of part.split("\n")) {
              if (line.startsWith("event:")) eventType = line.slice(6).trim()
              else if (line.startsWith("data:")) data += line.slice(5).trim()
              else if (line.startsWith("id:")) id = line.slice(3).trim()
            }

            if (id) lastEventIdRef.current = id

            if (eventType === MESSAGE_RECEIVED) {
              void queryClient.invalidateQueries({
                predicate: (q) => {
                  const key = q.queryKey[0]
                  return (
                    typeof key === "string" &&
                    key.includes("/api/chat/rooms/") &&
                    key.endsWith("/messages")
                  )
                },
              })
            }

            if (eventType === TRADE_STATE_CHANGED) {
              let tradeId: string | undefined
              try {
                const parsed = JSON.parse(data) as { tradeId?: string }
                tradeId = parsed.tradeId
              } catch {
                // ignore parse errors
              }
              if (tradeId) {
                void queryClient.invalidateQueries({
                  queryKey: [`/api/trades/${tradeId}`],
                })
              } else {
                void queryClient.invalidateQueries({
                  predicate: (q) => {
                    const key = q.queryKey[0]
                    return typeof key === "string" && key.startsWith("/api/trades/")
                  },
                })
              }
            }
          }
        }
      } catch {
        // reconnect below
      }

      if (!cancelled) {
        retryTimer = setTimeout(() => void connect(), 2000)
      }
    }

    void connect()

    return () => {
      cancelled = true
      if (retryTimer) clearTimeout(retryTimer)
    }
  }, [enabled, queryClient])
}
