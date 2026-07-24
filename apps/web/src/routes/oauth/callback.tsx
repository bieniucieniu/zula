import { createFileRoute } from "@tanstack/react-router"
import { useEffect } from "react"
import { handleOAuthCallbackPage } from "@/lib/oauth/popup-sign-in"

export const Route = createFileRoute("/oauth/callback")({
  component: OAuthCallbackPage,
})

function OAuthCallbackPage() {
  useEffect(() => {
    handleOAuthCallbackPage()
  }, [])

  return (
    <div className="flex min-h-svh items-center justify-center p-6">
      <p className="text-sm text-muted-foreground">Completing sign-in…</p>
    </div>
  )
}
