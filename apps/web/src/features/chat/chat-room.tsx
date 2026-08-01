import {
  getListChatMessagesQueryKey,
  useListChatMessages,
  useSendChatMessage,
} from "@zula/api/endpoints"
import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { useEventStream } from "@/features/chat/use-event-stream"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

export function ChatRoom({ id }: { id: string }) {
  useEventStream(true)
  const queryClient = useQueryClient()
  const messagesQuery = useListChatMessages(id)
  const send = useSendChatMessage()
  const [body, setBody] = useState("")
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const messages = messagesQuery.data?.data.messages ?? []

  async function onSend(e: React.FormEvent) {
    e.preventDefault()
    if (!body.trim()) return
    setPending(true)
    setError(null)
    try {
      await send.mutateAsync({
        id,
        data: {
          body: body.trim(),
          clientMessageId: crypto.randomUUID(),
        },
      })
      setBody("")
      await queryClient.invalidateQueries({ queryKey: getListChatMessagesQueryKey(id) })
    } catch (err) {
      setError(err instanceof Error ? err.message : "Send failed")
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="space-y-4 text-sm">
      <h1 className="font-heading text-lg font-medium">Chat</h1>

      {messagesQuery.isLoading ? (
        <p className="text-muted-foreground">Loading messages…</p>
      ) : messages.length === 0 ? (
        <p className="text-muted-foreground">No messages yet.</p>
      ) : (
        <ul className="max-h-[60vh] space-y-2 overflow-y-auto rounded-md border p-3">
          {messages.map((m) => (
            <li key={m.id} className="space-y-0.5">
              <p>{m.body}</p>
              <p className="text-xs text-muted-foreground">{m.senderId}</p>
            </li>
          ))}
        </ul>
      )}

      <form className="flex gap-2" onSubmit={(e) => void onSend(e)}>
        <Input value={body} onChange={(e) => setBody(e.target.value)} placeholder="Message" />
        <Button type="submit" size="sm" disabled={pending || !body.trim()}>
          {pending ? "…" : "Send"}
        </Button>
      </form>

      {error ? <p className="text-destructive">{error}</p> : null}
    </div>
  )
}
