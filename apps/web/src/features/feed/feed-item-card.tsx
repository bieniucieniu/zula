import type { FeedItemResponse } from "@zula/api"
import { Link } from "@tanstack/react-router"

export function FeedItemCard({ item }: { item: FeedItemResponse }) {
  const preview = item.media?.find((m) => m.publicUrl)?.publicUrl

  return (
    <Link
      to="/feed/items/$id"
      params={{ id: item.id }}
      className="block space-y-2 border-b py-3 text-sm last:border-b-0 hover:bg-muted/40"
    >
      <div className="flex items-baseline justify-between gap-2">
        <h3 className="font-medium">{item.title}</h3>
        <span className="shrink-0 text-xs text-muted-foreground">{item.kind}</span>
      </div>
      <p className="text-xs text-muted-foreground">
        {item.likeCount} likes · {item.commentCount} comments
      </p>
      {preview ? (
        <img src={preview} alt="" className="max-h-40 w-full rounded-md object-cover" />
      ) : null}
    </Link>
  )
}
