import { useQueryClient } from "@tanstack/react-query"
import type { MyProfileResponse, SellerProfileResponse } from "@zula/api"
import {
  getGetMyProfileQueryKey,
  getGetSellerProfileQueryKey,
  useUpdateMyProfile,
} from "@zula/api/endpoints"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { useAppForm } from "@/lib/form"

function Field({
  label,
  htmlFor,
  children,
  hint,
}: {
  label: string
  htmlFor: string
  children: React.ReactNode
  hint?: string
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  )
}

function Section({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children: React.ReactNode
}) {
  return (
    <section className="space-y-4 border-t pt-6 first:border-t-0 first:pt-0">
      <div className="space-y-1">
        <h2 className="font-heading text-sm font-medium uppercase tracking-wide text-muted-foreground">
          {title}
        </h2>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {children}
    </section>
  )
}

export function ProfileEditForm({
  me,
  profile,
  onSaved,
}: {
  me: MyProfileResponse
  profile: SellerProfileResponse
  onSaved?: () => void
}) {
  const queryClient = useQueryClient()
  const updateProfile = useUpdateMyProfile()
  const [error, setError] = useState<string | null>(null)

  const form = useAppForm({
    defaultValues: {
      displayName: profile.displayName ?? "",
      sellerHeadline: profile.sellerHeadline ?? "",
      avatarUrl: profile.avatarUrl ?? "",
      locationTag: profile.locationTag ?? "",
      bio: profile.bio?.sourceMarkdown ?? "",
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
            bioExpectedRevision: profile.bio?.revision ?? null,
            locationTag: value.locationTag.trim() || null,
            timezone: value.timezone.trim() || null,
            preferredLanguage: value.preferredLanguage.trim() || null,
            avatarUrl: value.avatarUrl.trim() || null,
          },
        })
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: getGetMyProfileQueryKey() }),
          queryClient.invalidateQueries({
            queryKey: getGetSellerProfileQueryKey(profile.username),
          }),
        ])
        onSaved?.()
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
      className="space-y-2"
      onSubmit={(event) => {
        event.preventDefault()
        event.stopPropagation()
        void form.handleSubmit()
      }}
    >
      <Section title="Identity" description="How you appear on your public profile.">
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
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-muted-foreground">Username</dt>
            <dd>@{profile.username}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Email</dt>
            <dd>{me.email || "—"}</dd>
          </div>
        </dl>
      </Section>

      <Section title="Bio" description="Markdown body shown on the About tab.">
        <form.Field name="bio">
          {(field) => (
            <Field label="Bio" htmlFor={field.name}>
              <Textarea
                id={field.name}
                name={field.name}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(event) => field.handleChange(event.target.value)}
                rows={12}
                className="font-mono text-sm"
              />
            </Field>
          )}
        </form.Field>
      </Section>

      <Section
        title="Preferences"
        description="Private settings — not shown on the public profile."
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <form.Field name="timezone">
            {(field) => (
              <Field label="Timezone" htmlFor={field.name} hint="IANA zone, e.g. Europe/Warsaw">
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
              <Field label="Language" htmlFor={field.name} hint="ISO 639-1, e.g. en">
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
        {me.isAdmin ? <p className="text-sm text-muted-foreground">Role: Admin</p> : null}
      </Section>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <div className="flex flex-wrap gap-2 border-t pt-6">
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(isSubmitting) => {
            const busy = isSubmitting || updateProfile.isPending
            return (
              <Button type="submit" disabled={busy}>
                {busy ? "Saving…" : "Save profile"}
              </Button>
            )
          }}
        </form.Subscribe>
      </div>
    </form>
  )
}
