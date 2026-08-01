import { createFileRoute, Outlet } from "@tanstack/react-router"
import { AppShell, RequireAuth } from "@/features/app/app-shell"

export const Route = createFileRoute("/chat")({
  component: ChatLayout,
})

function ChatLayout() {
  return (
    <RequireAuth>
      <AppShell>
        <Outlet />
      </AppShell>
    </RequireAuth>
  )
}
