import { createFileRoute, Outlet } from "@tanstack/react-router"
import { AppShell, RequireAuth } from "@/features/app/app-shell"

export const Route = createFileRoute("/feed")({
  component: FeedLayout,
})

function FeedLayout() {
  return (
    <RequireAuth>
      <AppShell>
        <Outlet />
      </AppShell>
    </RequireAuth>
  )
}
