import { createFileRoute } from "@tanstack/react-router"
import { ChatRoom } from "@/features/chat/chat-room"

export const Route = createFileRoute("/chat/rooms/$id")({
  component: ChatRoomPage,
  head: () => ({
    meta: [{ title: "Chat · Zula" }],
  }),
})

function ChatRoomPage() {
  const { id } = Route.useParams()
  return <ChatRoom id={id} />
}
