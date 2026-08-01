import { createFileRoute } from "@tanstack/react-router"
import { CreateFeedForm } from "@/features/feed/create-feed-form"

type FeedNewSearch = {
  groupId?: string
}

export const Route = createFileRoute("/feed/new")({
  validateSearch: (search: Record<string, unknown>): FeedNewSearch => ({
    groupId: typeof search.groupId === "string" ? search.groupId : undefined,
  }),
  component: FeedNewPage,
  head: () => ({
    meta: [{ title: "New feed item · Zula" }],
  }),
})

function FeedNewPage() {
  const { groupId } = Route.useSearch()
  return <CreateFeedForm groupId={groupId} />
}
