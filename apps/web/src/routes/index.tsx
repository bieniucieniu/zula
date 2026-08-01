import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useEffect } from "react"
import { useAuth } from "@/lib/auth"

export const Route = createFileRoute("/")({
  component: HomeRedirect,
})

function HomeRedirect() {
  const { session, ready } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!ready) return
    if (session) {
      void navigate({ to: "/feed" })
    } else {
      void navigate({ to: "/login" })
    }
  }, [ready, session, navigate])

  return (
    <div className="flex min-h-svh items-center justify-center p-6">
      <p className="text-sm text-muted-foreground">Loading…</p>
    </div>
  )
}
