import { createFileRoute } from "@tanstack/react-router"
import { FeedItemDetail } from "@/features/feed/feed-item-detail"

export const Route = createFileRoute("/feed/items/$id")({
  component: FeedItemPage,
  head: () => ({
    meta: [{ title: "Feed item · Zula" }],
  }),
})

function FeedItemPage() {
  const { id } = Route.useParams()
  return <FeedItemDetail id={id} />
}
