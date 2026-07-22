import { createFileRoute } from "@tanstack/react-router"
import { useEffect } from "react"
import { handleOAuthCallbackPage } from "@/lib/oauth/sign-in"

export const Route = createFileRoute("/api/auth/callback/google")({
  component: GoogleOAuthCallbackPage,
})

function GoogleOAuthCallbackPage() {
  useEffect(() => {
    handleOAuthCallbackPage()
  }, [])

  return (
    <div className="flex min-h-svh items-center justify-center p-6">
      <p className="text-sm text-muted-foreground">Completing sign-in…</p>
    </div>
  )
}
