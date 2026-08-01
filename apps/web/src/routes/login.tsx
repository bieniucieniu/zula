import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useEffect } from "react"
import { LoginForm } from "@/features/auth/login-form"
import { useAuth } from "@/lib/auth"

export const Route = createFileRoute("/login")({
  component: LoginPage,
})

function LoginPage() {
  const { session, ready } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (ready && session) {
      void navigate({ to: "/feed" })
    }
  }, [ready, session, navigate])

  if (!ready || session) {
    return (
      <div className="flex min-h-svh w-full items-center justify-center p-6">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </div>
    )
  }

  return (
    <div className="flex min-h-svh w-full items-center justify-center p-6 md:p-10">
      <div className="w-full max-w-sm">
        <LoginForm />
      </div>
    </div>
  )
}
