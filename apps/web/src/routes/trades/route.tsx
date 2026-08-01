import { createFileRoute, Outlet } from "@tanstack/react-router"
import { AppShell, RequireAuth } from "@/features/app/app-shell"

export const Route = createFileRoute("/trades")({
  component: TradesLayout,
})

function TradesLayout() {
  return (
    <RequireAuth>
      <AppShell>
        <Outlet />
      </AppShell>
    </RequireAuth>
  )
}
