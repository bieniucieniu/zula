import { createFileRoute, Outlet } from "@tanstack/react-router"
import { AppShell, RequireAuth } from "@/features/app/app-shell"

export const Route = createFileRoute("/groups")({
  component: GroupsLayout,
})

function GroupsLayout() {
  return (
    <RequireAuth>
      <AppShell>
        <Outlet />
      </AppShell>
    </RequireAuth>
  )
}
