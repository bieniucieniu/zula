import {
  getGetGroupQueryKey,
  getListGroupMembersQueryKey,
  useGetGroup,
  useJoinGroup,
  useLeaveGroup,
  useListGroupMembers,
} from "@zula/api/endpoints"
import { Link } from "@tanstack/react-router"
import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { FeedList } from "@/features/feed/feed-list"
import { Button, buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export function GroupDetail({ id }: { id: string }) {
  const queryClient = useQueryClient()
  const groupQuery = useGetGroup(id)
  const group = groupQuery.data?.data
  const membersQuery = useListGroupMembers(group?.id ?? "", undefined, {
    query: { enabled: !!group?.id },
  })
  const join = useJoinGroup()
  const leave = useLeaveGroup()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const members = membersQuery.data?.data.members ?? []
  const isMember = !!group?.viewerRole

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: getGetGroupQueryKey(id) })
    if (group?.id) {
      await queryClient.invalidateQueries({ queryKey: getListGroupMembersQueryKey(group.id) })
    }
  }

  async function onJoinLeave() {
    if (!group) return
    setPending(true)
    setError(null)
    try {
      if (isMember) {
        await leave.mutateAsync({ id: group.id })
      } else {
        await join.mutateAsync({ id: group.id })
      }
      await refresh()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action failed")
    } finally {
      setPending(false)
    }
  }

  if (groupQuery.isLoading) {
    return <p className="text-sm text-muted-foreground">Loading group…</p>
  }

  if (groupQuery.isError || !group) {
    return (
      <div className="space-y-2 text-sm">
        <p className="text-destructive">Could not load group.</p>
        <Button variant="outline" size="sm" onClick={() => void groupQuery.refetch()}>
          Retry
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6 text-sm">
      <div className="space-y-2">
        <div className="flex items-start justify-between gap-2">
          <div className="space-y-1">
            <h1 className="font-heading text-lg font-medium">{group.title}</h1>
            <p className="text-xs text-muted-foreground">
              @{group.slug} · {group.visibility}
              {group.viewerRole ? ` · ${group.viewerRole}` : ""}
            </p>
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={pending}
              onClick={() => void onJoinLeave()}
            >
              {pending ? "…" : isMember ? "Leave" : "Join"}
            </Button>
            <Link
              to="/feed/new"
              search={{ groupId: group.id }}
              className={cn(buttonVariants({ size: "sm" }))}
            >
              Post
            </Link>
          </div>
        </div>
        {group.description ? <p className="text-muted-foreground">{group.description}</p> : null}
        {error ? <p className="text-destructive">{error}</p> : null}
      </div>

      <section className="space-y-2">
        <h2 className="font-heading text-sm font-medium">Members</h2>
        {membersQuery.isLoading ? (
          <p className="text-muted-foreground">Loading members…</p>
        ) : members.length === 0 ? (
          <p className="text-muted-foreground">No members.</p>
        ) : (
          <ul className="space-y-1 text-xs">
            {members.map((m) => (
              <li key={`${m.groupId}-${m.userId}`}>
                {m.userId} · {m.role}
              </li>
            ))}
          </ul>
        )}
      </section>

      <FeedList groupId={group.id} />
    </div>
  )
}
