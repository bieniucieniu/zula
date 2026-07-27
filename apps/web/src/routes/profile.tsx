import { createFileRoute, Link, useNavigate } from "@tanstack/react-router"
import { useEffect, useState } from "react"
import {
  ProfileShell,
  RecentReviews,
  SellerBio,
  SellerProfileHeader,
  SellerTrustRow,
} from "@/components/profile/seller-profile-view"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { useAuth } from "@/lib/auth"
import {
  type UpdateMyProfileRequest,
  useMyProfile,
  useUpdateMyProfile,
} from "@/lib/profile-api"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/profile")({
  component: ProfilePage,
  head: () => ({
    meta: [{ title: "Profile · Zula" }],
  }),
})

function ProfilePage() {
  const { session, ready } = useAuth()
  const navigate = useNavigate()
  const profileQuery = useMyProfile(ready && !!session)
  const updateProfile = useUpdateMyProfile()
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<string | null>(null)

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

  const me = profileQuery.data.data
  const profile = me.public

  async function onSave(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    const form = new FormData(event.currentTarget)
    const body: UpdateMyProfileRequest = {
      displayName: String(form.get("displayName") ?? "").trim() || null,
      sellerHeadline: String(form.get("sellerHeadline") ?? "").trim() || null,
      bio: String(form.get("bio") ?? ""),
      locationTag: String(form.get("locationTag") ?? "").trim() || null,
      timezone: String(form.get("timezone") ?? "").trim() || null,
      preferredLanguage: String(form.get("preferredLanguage") ?? "").trim() || null,
      avatarUrl: String(form.get("avatarUrl") ?? "").trim() || null,
    }
    try {
      await updateProfile.mutateAsync(body)
      setEditing(false)
    } catch (err) {
      const detail =
        err && typeof err === "object" && "detail" in err
          ? String((err as { detail?: unknown }).detail)
          : "Save failed"
      setError(detail)
    }
  }

  return (
    <ProfileShell title="Your profile">
      <SellerProfileHeader
        profile={profile}
        actions={
          <>
            <Link
              to="/sellers/$idOrUsername"
              params={{ idOrUsername: profile.username }}
              className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
            >
              Public view
            </Link>
            <Button
              variant={editing ? "secondary" : "outline"}
              size="sm"
              onClick={() => {
                setEditing((value) => !value)
                setError(null)
              }}
            >
              {editing ? "Cancel" : "Edit"}
            </Button>
          </>
        }
      />

      <SellerTrustRow profile={profile} />

      {editing ? (
        <form className="space-y-4" onSubmit={(event) => void onSave(event)}>
          <Field label="Display name" htmlFor="displayName">
            <Input
              id="displayName"
              name="displayName"
              defaultValue={profile.displayName ?? ""}
              maxLength={100}
            />
          </Field>
          <Field label="Headline" htmlFor="sellerHeadline">
            <Input
              id="sellerHeadline"
              name="sellerHeadline"
              defaultValue={profile.sellerHeadline ?? ""}
              maxLength={160}
            />
          </Field>
          <Field label="Bio" htmlFor="bio">
            <Textarea id="bio" name="bio" defaultValue={profile.bio ?? ""} maxLength={2000} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Location tag" htmlFor="locationTag">
              <Input
                id="locationTag"
                name="locationTag"
                defaultValue={profile.locationTag ?? ""}
                maxLength={100}
              />
            </Field>
            <Field label="Avatar URL" htmlFor="avatarUrl">
              <Input
                id="avatarUrl"
                name="avatarUrl"
                defaultValue={profile.avatarUrl ?? ""}
                maxLength={2048}
              />
            </Field>
            <Field label="Timezone" htmlFor="timezone">
              <Input
                id="timezone"
                name="timezone"
                defaultValue={me.timezone ?? ""}
                placeholder="Europe/Warsaw"
              />
            </Field>
            <Field label="Language" htmlFor="preferredLanguage">
              <Input
                id="preferredLanguage"
                name="preferredLanguage"
                defaultValue={me.preferredLanguage ?? ""}
                placeholder="en"
                maxLength={2}
              />
            </Field>
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <Button type="submit" disabled={updateProfile.isPending}>
            {updateProfile.isPending ? "Saving…" : "Save profile"}
          </Button>
        </form>
      ) : (
        <section className="space-y-6">
          <div className="space-y-2">
            <h2 className="font-heading text-sm font-medium uppercase tracking-wide text-muted-foreground">
              About
            </h2>
            <SellerBio profile={profile} />
          </div>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">Email</dt>
              <dd>{me.email || "—"}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Timezone</dt>
              <dd>{me.timezone || "—"}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Language</dt>
              <dd>{me.preferredLanguage || "—"}</dd>
            </div>
            {me.isAdmin ? (
              <div>
                <dt className="text-muted-foreground">Role</dt>
                <dd>Admin</dd>
              </div>
            ) : null}
          </dl>
          <div className="space-y-2">
            <h2 className="font-heading text-sm font-medium uppercase tracking-wide text-muted-foreground">
              Recent reviews
            </h2>
            <RecentReviews profile={profile} />
          </div>
        </section>
      )}
    </ProfileShell>
  )
}

function Field({
  label,
  htmlFor,
  children,
}: {
  label: string
  htmlFor: string
  children: React.ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
    </div>
  )
}
