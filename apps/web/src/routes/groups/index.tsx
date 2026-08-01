import { createFileRoute } from "@tanstack/react-router"
import { GroupsHome } from "@/features/groups/groups-home"

export const Route = createFileRoute("/groups/")({
  component: GroupsIndexPage,
  head: () => ({
    meta: [{ title: "Groups · Zula" }],
  }),
})

function GroupsIndexPage() {
  return <GroupsHome />
}
