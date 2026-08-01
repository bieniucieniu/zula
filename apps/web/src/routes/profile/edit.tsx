import { createFileRoute, Link, useNavigate } from "@tanstack/react-router"
import { useGetMyProfile } from "@zula/api/endpoints"
import { useEffect } from "react"
import { ProfileEditForm } from "@/features/user/profile-edit-form"
import { ProfileShell } from "@/features/user/seller-profile-view"
import { Button, buttonVariants } from "@/components/ui/button"
import { useAuth } from "@/lib/auth"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/profile/edit")({
  component: ProfileEditPage,
  head: () => ({
    meta: [{ title: "Edit profile · Zula" }],
  }),
})

function ProfileEditPage() {
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
      <ProfileShell title="Edit profile">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </ProfileShell>
    )
  }

  if (profileQuery.isError || !profileQuery.data?.data) {
    return (
      <ProfileShell title="Edit profile">
        <p className="text-sm text-destructive">Could not load profile.</p>
        <Button variant="outline" onClick={() => void profileQuery.refetch()}>
          Retry
        </Button>
      </ProfileShell>
    )
  }

  const me = profileQuery.data.data
  const profile = me.public

  return (
    <ProfileShell title="Edit profile">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-heading text-2xl font-medium tracking-tight">Edit profile</h1>
        <div className="flex flex-wrap gap-2">
          <Link
            to="/profile/portfolio"
            className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
          >
            Edit portfolio
          </Link>
          <Link to="/profile" className={cn(buttonVariants({ variant: "outline", size: "sm" }))}>
            Cancel
          </Link>
        </div>
      </div>

      <ProfileEditForm
        me={me}
        profile={profile}
        onSaved={() => {
          void navigate({ to: "/profile" })
        }}
      />
    </ProfileShell>
  )
}
