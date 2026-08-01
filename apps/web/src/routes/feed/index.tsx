import { createFileRoute } from "@tanstack/react-router"
import { FeedList } from "@/features/feed/feed-list"

export const Route = createFileRoute("/feed/")({
  component: FeedIndexPage,
  head: () => ({
    meta: [{ title: "Feed · Zula" }],
  }),
})

function FeedIndexPage() {
  return <FeedList />
}
