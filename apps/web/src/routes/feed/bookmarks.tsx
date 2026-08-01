import { createFileRoute } from "@tanstack/react-router"
import { BookmarksList } from "@/features/feed/bookmarks-list"

export const Route = createFileRoute("/feed/bookmarks")({
  component: FeedBookmarksPage,
  head: () => ({
    meta: [{ title: "Bookmarks · Zula" }],
  }),
})

function FeedBookmarksPage() {
  return <BookmarksList />
}
