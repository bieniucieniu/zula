import type { FeedItemResponse } from "@zula/api"
import { useListFeed, useListFeedByGroup } from "@zula/api/endpoints"
import { Link } from "@tanstack/react-router"
import { useEffect, useState } from "react"
import { FeedItemCard } from "@/features/feed/feed-item-card"
import { Button, buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"

type FeedListProps = {
  groupId?: string
}

export function FeedList({ groupId }: FeedListProps) {
  const [cursor, setCursor] = useState<string | undefined>()
  const [items, setItems] = useState<FeedItemResponse[]>([])
  const [hasMore, setHasMore] = useState(false)

  const globalFeed = useListFeed(cursor ? { cursor } : undefined, {
    query: { enabled: !groupId },
  })
  const groupFeed = useListFeedByGroup(groupId ?? "", cursor ? { cursor } : undefined, {
    query: { enabled: !!groupId },
  })

  const query = groupId ? groupFeed : globalFeed
  const page = query.data?.data

  useEffect(() => {
    if (!page) return
    setItems((prev) => {
      if (!cursor) return page.items
      const seen = new Set(prev.map((i) => i.id))
      return [...prev, ...page.items.filter((i) => !seen.has(i.id))]
    })
    setHasMore(page.hasMore)
  }, [page, cursor])

  if (query.isLoading && items.length === 0) {
    return <p className="text-sm text-muted-foreground">Loading feed…</p>
  }

  if (query.isError && items.length === 0) {
    return (
      <div className="space-y-2 text-sm">
        <p className="text-destructive">Could not load feed.</p>
        <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
          Retry
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="font-heading text-lg font-medium">{groupId ? "Group feed" : "Feed"}</h1>
        <div className="flex gap-2">
          <Link
            to="/feed/bookmarks"
            className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
          >
            Bookmarks
          </Link>
          <Link
            to="/feed/new"
            search={groupId ? { groupId } : {}}
            className={cn(buttonVariants({ size: "sm" }))}
          >
            New
          </Link>
        </div>
      </div>

      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No items yet.</p>
      ) : (
        <div className="divide-y rounded-md border px-3">
          {items.map((item) => (
            <FeedItemCard key={item.id} item={item} />
          ))}
        </div>
      )}

      {hasMore && page?.nextCursor?.id ? (
        <Button
          variant="outline"
          size="sm"
          disabled={query.isFetching}
          onClick={() => setCursor(page.nextCursor!.id)}
        >
          {query.isFetching ? "Loading…" : "Load more"}
        </Button>
      ) : null}
    </div>
  )
}
