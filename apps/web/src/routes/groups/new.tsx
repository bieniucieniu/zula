import { createFileRoute } from "@tanstack/react-router"
import { CreateGroupForm } from "@/features/groups/create-group-form"

export const Route = createFileRoute("/groups/new")({
  component: GroupsNewPage,
  head: () => ({
    meta: [{ title: "Create group · Zula" }],
  }),
})

function GroupsNewPage() {
  return <CreateGroupForm />
}
