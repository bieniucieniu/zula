import { createFileRoute, Link, useNavigate } from "@tanstack/react-router"
import { useGetMyProfile } from "@zula/api/endpoints"
import { useEffect } from "react"
import { OwnPortfolioManager } from "@/features/user/portfolio-edit"
import { ProfileShell } from "@/features/user/seller-profile-view"
import { Button, buttonVariants } from "@/components/ui/button"
import { useAuth } from "@/lib/auth"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/profile/portfolio")({
  component: ProfilePortfolioPage,
  head: () => ({
    meta: [{ title: "Edit portfolio · Zula" }],
  }),
})

function ProfilePortfolioPage() {
  const { session, ready } = useAuth()
  const navigate = useNavigate()
  const profileQuery = useGetMyProfile({ query: { enabled: ready && !!session } })

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

  if (profileQuery.isLoading) {
    return (
      <ProfileShell title="Edit portfolio">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </ProfileShell>
    )
  }

  if (profileQuery.isError || !profileQuery.data?.data) {
    return (
      <ProfileShell title="Edit portfolio">
        <p className="text-sm text-destructive">Could not load profile.</p>
        <Button variant="outline" onClick={() => void profileQuery.refetch()}>
          Retry
        </Button>
      </ProfileShell>
    )
  }

  const profile = profileQuery.data.data.public

  return (
    <ProfileShell title="Edit portfolio">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-1">
          <h1 className="font-heading text-2xl font-medium tracking-tight">Portfolio</h1>
          <p className="text-sm text-muted-foreground">
            Add, pin, or remove work shown on your public profile.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            to="/profile/edit"
            className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
          >
            Edit profile
          </Link>
          <Link to="/profile" className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
            Done
          </Link>
        </div>
      </div>

      <OwnPortfolioManager profile={profile} />
    </ProfileShell>
  )
}
