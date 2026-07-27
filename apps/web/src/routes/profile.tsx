import { createFileRoute, Link, useNavigate } from "@tanstack/react-router"
import { useGetMyProfile, useUpdateMyProfile } from "@zula/api/endpoints"
import type { MyProfileResponse, SellerProfileResponse } from "@zula/api"
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
import { useAppForm } from "@/lib/form"
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
  const profileQuery = useGetMyProfile({ query: { enabled: ready && !!session } })
  const [editing, setEditing] = useState(false)

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
              onClick={() => setEditing((value) => !value)}
            >
              {editing ? "Cancel" : "Edit"}
            </Button>
          </>
        }
      />

      <SellerTrustRow profile={profile} />

      {editing ? (
        <ProfileEditForm me={me} profile={profile} onSaved={() => setEditing(false)} />
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

function ProfileEditForm({
  me,
  profile,
  onSaved,
}: {
  me: MyProfileResponse
  profile: SellerProfileResponse
  onSaved: () => void
}) {
  const updateProfile = useUpdateMyProfile()
  const [error, setError] = useState<string | null>(null)

  const form = useAppForm({
    defaultValues: {
      displayName: profile.displayName ?? "",
      sellerHeadline: profile.sellerHeadline ?? "",
      bio: profile.bio ?? "",
      locationTag: profile.locationTag ?? "",
      avatarUrl: profile.avatarUrl ?? "",
      timezone: me.timezone ?? "",
      preferredLanguage: me.preferredLanguage ?? "",
    },
    onSubmit: async ({ value }) => {
      setError(null)
      try {
        await updateProfile.mutateAsync({
          data: {
            displayName: value.displayName.trim() || null,
            sellerHeadline: value.sellerHeadline.trim() || null,
            bio: value.bio,
            locationTag: value.locationTag.trim() || null,
            timezone: value.timezone.trim() || null,
            preferredLanguage: value.preferredLanguage.trim() || null,
            avatarUrl: value.avatarUrl.trim() || null,
          },
        })
        onSaved()
      } catch (err) {
        const detail =
          err && typeof err === "object" && "detail" in err
            ? String((err as { detail?: unknown }).detail)
            : "Save failed"
        setError(detail)
      }
    },
  })

  return (
    <form
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault()
        event.stopPropagation()
        void form.handleSubmit()
      }}
    >
      <form.Field name="displayName">
        {(field) => (
          <Field label="Display name" htmlFor={field.name}>
            <Input
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              maxLength={100}
            />
          </Field>
        )}
      </form.Field>
      <form.Field name="sellerHeadline">
        {(field) => (
          <Field label="Headline" htmlFor={field.name}>
            <Input
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              maxLength={160}
            />
          </Field>
        )}
      </form.Field>
      <form.Field name="bio">
        {(field) => (
          <Field label="Bio" htmlFor={field.name}>
            <Textarea
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              maxLength={2000}
            />
          </Field>
        )}
      </form.Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <form.Field name="locationTag">
          {(field) => (
            <Field label="Location tag" htmlFor={field.name}>
              <Input
                id={field.name}
                name={field.name}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(event) => field.handleChange(event.target.value)}
                maxLength={100}
              />
            </Field>
          )}
        </form.Field>
        <form.Field name="avatarUrl">
          {(field) => (
            <Field label="Avatar URL" htmlFor={field.name}>
              <Input
                id={field.name}
                name={field.name}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(event) => field.handleChange(event.target.value)}
                maxLength={2048}
              />
            </Field>
          )}
        </form.Field>
        <form.Field name="timezone">
          {(field) => (
            <Field label="Timezone" htmlFor={field.name}>
              <Input
                id={field.name}
                name={field.name}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder="Europe/Warsaw"
              />
            </Field>
          )}
        </form.Field>
        <form.Field name="preferredLanguage">
          {(field) => (
            <Field label="Language" htmlFor={field.name}>
              <Input
                id={field.name}
                name={field.name}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(event) => field.handleChange(event.target.value)}
                placeholder="en"
                maxLength={2}
              />
            </Field>
          )}
        </form.Field>
      </div>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <form.Subscribe selector={(state) => state.isSubmitting}>
        {(isSubmitting) => (
          <Button type="submit" disabled={isSubmitting || updateProfile.isPending}>
            {isSubmitting || updateProfile.isPending ? "Saving…" : "Save profile"}
          </Button>
        )}
      </form.Subscribe>
    </form>
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
