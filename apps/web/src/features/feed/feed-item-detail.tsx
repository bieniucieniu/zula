import {
  getGetFeedItemQueryKey,
  getListCommentsQueryKey,
  useAddComment,
  useBookmarkFeedItem,
  useGetFeedItem,
  useLikeFeedItem,
  useListComments,
  useUnbookmarkFeedItem,
  useUnlikeFeedItem,
} from "@zula/api/endpoints"
import { Link } from "@tanstack/react-router"
import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { cn } from "@/lib/utils"

export function FeedItemDetail({ id }: { id: string }) {
  const queryClient = useQueryClient()
  const itemQuery = useGetFeedItem(id)
  const commentsQuery = useListComments(id)
  const like = useLikeFeedItem()
  const unlike = useUnlikeFeedItem()
  const bookmark = useBookmarkFeedItem()
  const unbookmark = useUnbookmarkFeedItem()
  const addComment = useAddComment()

  const [commentBody, setCommentBody] = useState("")
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const item = itemQuery.data?.data
  const comments = commentsQuery.data?.data.comments ?? []

  async function refreshItem() {
    await queryClient.invalidateQueries({ queryKey: getGetFeedItemQueryKey(id) })
  }

  async function onToggleLike() {
    if (!item) return
    setError(null)
    try {
      if (item.likedByViewer) {
        await unlike.mutateAsync({ id })
      } else {
        await like.mutateAsync({ id })
      }
      await refreshItem()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Like failed")
    }
  }

  async function onToggleBookmark() {
    if (!item) return
    setError(null)
    try {
      if (item.bookmarkedByViewer) {
        await unbookmark.mutateAsync({ id })
      } else {
        await bookmark.mutateAsync({ id })
      }
      await refreshItem()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Bookmark failed")
    }
  }

  async function onAddComment(e: React.FormEvent) {
    e.preventDefault()
    if (!commentBody.trim()) return
    setPending(true)
    setError(null)
    try {
      await addComment.mutateAsync({ id, data: { body: commentBody.trim() } })
      setCommentBody("")
      await queryClient.invalidateQueries({ queryKey: getListCommentsQueryKey(id) })
      await refreshItem()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Comment failed")
    } finally {
      setPending(false)
    }
  }

  if (itemQuery.isLoading) {
    return <p className="text-sm text-muted-foreground">Loading…</p>
  }

  if (itemQuery.isError || !item) {
    return (
      <div className="space-y-2 text-sm">
        <p className="text-destructive">Could not load item.</p>
        <Button variant="outline" size="sm" onClick={() => void itemQuery.refetch()}>
          Retry
        </Button>
      </div>
    )
  }

  const preview = item.media?.find((m) => m.publicUrl)?.publicUrl

  return (
    <div className="space-y-6 text-sm">
      <div className="space-y-2">
        <div className="flex items-baseline justify-between gap-2">
          <h1 className="font-heading text-lg font-medium">{item.title}</h1>
          <span className="text-xs text-muted-foreground">{item.kind}</span>
        </div>
        {item.body ? (
          <p className="whitespace-pre-wrap text-muted-foreground">{item.body}</p>
        ) : null}
        {preview ? (
          <img src={preview} alt="" className="max-h-80 w-full rounded-md object-cover" />
        ) : null}
        <p className="text-xs text-muted-foreground">
          {item.likeCount} likes · {item.commentCount} comments
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => void onToggleLike()}>
          {item.likedByViewer ? "Unlike" : "Like"}
        </Button>
        <Button variant="outline" size="sm" onClick={() => void onToggleBookmark()}>
          {item.bookmarkedByViewer ? "Remove bookmark" : "Bookmark"}
        </Button>
        <Link
          to="/trades/new"
          search={{ feedItemId: item.id, counterpartyId: item.authorId }}
          className={cn(buttonVariants({ size: "sm" }))}
        >
          Start trade
        </Link>
      </div>

      {error ? <p className="text-destructive">{error}</p> : null}

      <section className="space-y-3">
        <h2 className="font-heading text-sm font-medium">Comments</h2>
        {commentsQuery.isLoading ? (
          <p className="text-muted-foreground">Loading comments…</p>
        ) : comments.length === 0 ? (
          <p className="text-muted-foreground">No comments yet.</p>
        ) : (
          <ul className="space-y-2">
            {comments.map((c) => (
              <li key={c.id} className="rounded-md border px-3 py-2">
                <p>{c.body}</p>
                <p className="text-xs text-muted-foreground">{c.authorId}</p>
              </li>
            ))}
          </ul>
        )}

        <form className="flex gap-2" onSubmit={(e) => void onAddComment(e)}>
          <Input
            value={commentBody}
            onChange={(e) => setCommentBody(e.target.value)}
            placeholder="Add a comment"
          />
          <Button type="submit" size="sm" disabled={pending || !commentBody.trim()}>
            {pending ? "…" : "Post"}
          </Button>
        </form>
      </section>
    </div>
  )
}
