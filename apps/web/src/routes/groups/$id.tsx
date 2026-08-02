import { createFileRoute } from "@tanstack/react-router"
import { GroupDetail } from "@/features/groups/group-detail"

export const Route = createFileRoute("/groups/$id")({
  component: GroupDetailPage,
  head: () => ({
    meta: [{ title: "Group · Zula" }],
  }),
})

function GroupDetailPage() {
  const { id } = Route.useParams()
  return <GroupDetail id={id} />
}
