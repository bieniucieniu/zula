import { createFileRoute } from "@tanstack/react-router"
import { GroupDetail } from "@/features/groups/group-detail"

export const Route = createFileRoute("/groups/$idOrSlug")({
  component: GroupDetailPage,
  head: () => ({
    meta: [{ title: "Group · Zula" }],
  }),
})

function GroupDetailPage() {
  const { idOrSlug } = Route.useParams()
  return <GroupDetail idOrSlug={idOrSlug} />
}
