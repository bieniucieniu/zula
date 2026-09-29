import { createFileRoute, Link, useNavigate } from "@tanstack/react-router"
import {
  useBlockUser,
  useGetMyProfile,
  useGetSellerProfile,
  useUnblockUser,
} from "@zula/api/endpoints"
import { useState } from "react"
import { ProfileContentTabs } from "@/features/user/profile-tabs"
import {
  ProfileShell,
  SellerProfileHeader,
  SellerTrustRow,
} from "@/features/user/seller-profile-view"
import { Button, buttonVariants } from "@/components/ui/button"
import { useAuth } from "@/lib/auth"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/sellers/$id")({
  component: SellerPage,
  head: ({ params }) => ({
    meta: [{ title: `Seller ${params.id} · Zula` }],
  }),
})

function SellerPage() {
  const { id } = Route.useParams()
  const { session, ready } = useAuth()
  const navigate = useNavigate()
  const sellerQuery = useGetSellerProfile(id)
  const myProfileQuery = useGetMyProfile({
    query: {
      enabled: ready && !!session,
    },
  })
  const blockUser = useBlockUser()
  const unblockUser = useUnblockUser()
  const [actionError, setActionError] = useState<string | null>(null)

  if (sellerQuery.isLoading) {
    return (
      <ProfileShell title="Seller">
        <p className="text-sm text-muted-foreground">Loading…</p>
      </ProfileShell>
    )
  }

  if (sellerQuery.isError || !sellerQuery.data?.data) {
    return (
      <ProfileShell title="Seller">
        <p className="text-sm text-destructive">Seller not found.</p>
        <Link to="/" className={cn(buttonVariants({ variant: "outline" }))}>
          Back home
        </Link>
      </ProfileShell>
    )
  }

  const profile = sellerQuery.data.data
  const myUserId = myProfileQuery.data?.data.public.userId
  const isSelf = !!myUserId && myUserId === profile.userId
  const viewerHasBlocked = profile.viewerHasBlocked === true

  async function onToggleBlock() {
    setActionError(null)
    if (!session) {
      await navigate({ to: "/login" })
      return
    }
    try {
      if (viewerHasBlocked) {
        await unblockUser.mutateAsync({ userId: profile.userId })
      } else {
        await blockUser.mutateAsync({ userId: profile.userId })
      }
    } catch (err) {
      const detail =
        err && typeof err === "object" && "detail" in err
          ? String((err as { detail?: unknown }).detail)
          : "Action failed"
      setActionError(detail)
    }
  }

  return (
    <ProfileShell title={`@${profile.username}`}>
      <SellerProfileHeader
        profile={profile}
        actions={
          isSelf ? (
            <Link
              to="/profile/edit"
              className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
            >
              Edit profile
            </Link>
          ) : (
            <Button
              variant="outline"
              size="sm"
              disabled={blockUser.isPending || unblockUser.isPending}
              onClick={() => void onToggleBlock()}
            >
              {viewerHasBlocked ? "Unblock" : "Block"}
            </Button>
          )
        }
      />

      <SellerTrustRow profile={profile} />

      {actionError ? <p className="text-sm text-destructive">{actionError}</p> : null}

      <ProfileContentTabs profile={profile} id={id} canManagePortfolio={isSelf} />
    </ProfileShell>
  )
}
