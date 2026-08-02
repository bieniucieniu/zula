import { createFileRoute, Link } from "@tanstack/react-router"
import { useGetMyProfile } from "@zula/api/endpoints"
import { ProfileContentTabs } from "@/features/user/profile-tabs"
import {
  ProfileShell,
  SellerProfileHeader,
  SellerTrustRow,
} from "@/features/user/seller-profile-view"
import { Button, buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/profile/")({
  component: ProfilePage,
  head: () => ({
    meta: [{ title: "Profile · Zula" }],
  }),
})

function ProfilePage() {
  const profileQuery = useGetMyProfile()

  if (profileQuery.isLoading) {
    return (
      <ProfileShell title="Profile">
        <p className="text-sm text-muted-foreground">Loading profile…</p>
      </ProfileShell>
    )
  }

  if (profileQuery.isError || !profileQuery.data?.data) {
    return (
      <ProfileShell title="Profile">
        <p className="text-sm text-destructive">Could not load profile.</p>
        <Button variant="outline" onClick={() => void profileQuery.refetch()}>
          Retry
        </Button>
      </ProfileShell>
    )
  }

  const profile = profileQuery.data.data.public

  return (
    <ProfileShell title="Your profile">
      <SellerProfileHeader
        profile={profile}
        actions={
          <>
            <Link
              to="/profile/edit"
              className={cn(buttonVariants({ variant: "default", size: "sm" }))}
            >
              Edit
            </Link>
            <Link
              to="/sellers/$id"
              params={{ id: profile.userId }}
              className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
            >
              Public view
            </Link>
          </>
        }
      />

      <SellerTrustRow profile={profile} />

      <ProfileContentTabs profile={profile} id="me" canManagePortfolio />
    </ProfileShell>
  )
}
