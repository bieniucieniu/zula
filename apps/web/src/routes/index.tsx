import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/lib/auth"

export const Route = createFileRoute("/")({ component: App })

function App() {
  const { session, ready, logout } = useAuth()
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)

  useEffect(() => {
    if (ready && !session) {
      void navigate({ to: "/login" })
    }
  }, [ready, session, navigate])

  if (!ready || !session) {
    return (
      <div className="flex min-h-svh items-center justify-center p-6">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </div>
    )
  }

  async function onLogout() {
    setPending(true)
    try {
      await logout()
      await navigate({ to: "/login" })
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 p-6">
      <div className="flex max-w-sm flex-col items-center gap-2 text-center">
        <h1 className="font-heading text-lg font-medium">Signed in</h1>
        <p className="text-sm text-muted-foreground">{session.email}</p>
        <Button
          variant="outline"
          disabled={pending}
          onClick={() => void onLogout()}
        >
          {pending ? "Logging out…" : "Logout"}
        </Button>
      </div>
    </div>
  )
}
