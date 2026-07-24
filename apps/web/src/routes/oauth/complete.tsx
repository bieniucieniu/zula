import { createFileRoute } from "@tanstack/react-router"
import { useEffect } from "react"
import { useAuth } from "@/lib/auth"

export const Route = createFileRoute("/oauth/complete")({
  component: OAuthCompletePage,
})

function OAuthCompletePage() {
  const { refresh } = useAuth()

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const success = params.get("success") === "1"
    if (success) {
      void refresh().finally(() => {
        window.location.replace("/")
      })
      return
    }
    window.location.replace("/login")
  }, [refresh])

  return (
    <div className="flex min-h-svh items-center justify-center p-6">
      <p className="text-sm text-muted-foreground">Completing sign-in…</p>
    </div>
  )
}
