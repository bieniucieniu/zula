import { useQueryClient } from "@tanstack/react-query"
import type { PortfolioItem, SellerProfileResponse } from "@zula/api"
import { uploadImageObject } from "@zula/api"
import {
  getGetMyProfileQueryKey,
  getGetSellerProfileQueryKey,
  getListPortfolioItemsQueryKey,
  useDeletePortfolioItem,
  useListPortfolioItems,
  usePinPortfolioItem,
  useUnpinPortfolioItem,
  useUpsertPortfolioItem,
} from "@zula/api/endpoints"
import { useState } from "react"
import { PortfolioGrid } from "@/features/user/portfolio-view"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { useAppForm } from "@/lib/form"

function mutationError(err: unknown): string {
  if (err && typeof err === "object" && "detail" in err) {
    return String((err as { detail?: unknown }).detail)
  }
  if (err instanceof Error) return err.message
  return "Action failed"
}

function useInvalidatePortfolio(username: string) {
  const queryClient = useQueryClient()
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: getGetMyProfileQueryKey() }),
      queryClient.invalidateQueries({ queryKey: getGetSellerProfileQueryKey(username) }),
      queryClient.invalidateQueries({ queryKey: getListPortfolioItemsQueryKey("me") }),
      queryClient.invalidateQueries({ queryKey: getListPortfolioItemsQueryKey(username) }),
    ])
  }
}

export function PortfolioAddForm({
  profile,
  onSaved,
}: {
  profile: SellerProfileResponse
  onSaved?: () => void
}) {
  const upsert = useUpsertPortfolioItem()
  const invalidate = useInvalidatePortfolio(profile.username)
  const [error, setError] = useState<string | null>(null)
  const [coverPreview, setCoverPreview] = useState<string | null>(null)
  const [coverFile, setCoverFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)

  const form = useAppForm({
    defaultValues: {
      kind: "creation",
      title: "",
      summary: "",
      bodyMarkdown: "",
      externalUrl: "",
      visibility: "public",
    },
    onSubmit: async ({ value }) => {
      setError(null)
      try {
        let coverObjectKey: string | null = null
        if (coverFile) {
          setUploading(true)
          const uploaded = await uploadImageObject(coverFile)
          coverObjectKey = uploaded.objectKey
        }
        await upsert.mutateAsync({
          data: {
            kind: value.kind,
            title: value.title.trim(),
            summary: value.summary.trim() || null,
            bodyMarkdown: value.bodyMarkdown.trim() || null,
            externalUrl: value.kind === "external_link" ? value.externalUrl.trim() : null,
            coverObjectKey,
            visibility: value.visibility,
          },
        })
        form.reset()
        setCoverFile(null)
        setCoverPreview(null)
        await invalidate()
        onSaved?.()
      } catch (err) {
        setError(mutationError(err))
      } finally {
        setUploading(false)
      }
    },
  })

  return (
    <form
      className="space-y-3 border-t pt-4"
      onSubmit={(event) => {
        event.preventDefault()
        event.stopPropagation()
        void form.handleSubmit()
      }}
    >
      <p className="text-sm font-medium">Add portfolio item</p>
      <div className="space-y-1.5">
        <Label htmlFor="cover-file">Cover image</Label>
        <Input
          id="cover-file"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          onChange={(event) => {
            const file = event.target.files?.[0] ?? null
            setCoverFile(file)
            setCoverPreview(file ? URL.createObjectURL(file) : null)
          }}
        />
        {coverPreview ? (
          <img
            src={coverPreview}
            alt=""
            className="mt-2 aspect-[4/3] w-full max-w-xs object-cover bg-muted"
          />
        ) : null}
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <form.Field name="kind">
          {(field) => (
            <div className="space-y-1.5">
              <Label htmlFor={field.name}>Kind</Label>
              <select
                id={field.name}
                name={field.name}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(event) => field.handleChange(event.target.value)}
                className="border-input bg-background h-9 w-full rounded-md border px-3 text-sm"
              >
                <option value="creation">creation</option>
                <option value="case_study">case_study</option>
                <option value="external_link">external_link</option>
              </select>
            </div>
          )}
        </form.Field>
        <form.Field name="visibility">
          {(field) => (
            <div className="space-y-1.5">
              <Label htmlFor={field.name}>Visibility</Label>
              <select
                id={field.name}
                name={field.name}
                value={field.state.value}
                onBlur={field.handleBlur}
                onChange={(event) => field.handleChange(event.target.value)}
                className="border-input bg-background h-9 w-full rounded-md border px-3 text-sm"
              >
                <option value="public">public</option>
                <option value="unlisted">unlisted</option>
              </select>
            </div>
          )}
        </form.Field>
      </div>
      <form.Field name="title">
        {(field) => (
          <div className="space-y-1.5">
            <Label htmlFor={field.name}>Title</Label>
            <Input
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              maxLength={200}
              required
            />
          </div>
        )}
      </form.Field>
      <form.Field name="summary">
        {(field) => (
          <div className="space-y-1.5">
            <Label htmlFor={field.name}>Summary</Label>
            <Textarea
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              rows={2}
            />
          </div>
        )}
      </form.Field>
      <form.Field name="externalUrl">
        {(field) => (
          <div className="space-y-1.5">
            <Label htmlFor={field.name}>HTTPS URL (external_link)</Label>
            <Input
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              placeholder="https://"
            />
          </div>
        )}
      </form.Field>
      <form.Field name="bodyMarkdown">
        {(field) => (
          <div className="space-y-1.5">
            <Label htmlFor={field.name}>Body (markdown)</Label>
            <Textarea
              id={field.name}
              name={field.name}
              value={field.state.value}
              onBlur={field.handleBlur}
              onChange={(event) => field.handleChange(event.target.value)}
              rows={4}
              className="font-mono text-sm"
            />
          </div>
        )}
      </form.Field>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <form.Subscribe selector={(state) => state.isSubmitting}>
        {(isSubmitting) => {
          const busy = isSubmitting || upsert.isPending || uploading
          return (
            <Button type="submit" disabled={busy}>
              {busy ? "Saving…" : "Add item"}
            </Button>
          )
        }}
      </form.Subscribe>
    </form>
  )
}

export function OwnPortfolioManager({ profile }: { profile: SellerProfileResponse }) {
  const listQuery = useListPortfolioItems("me")
  const pin = usePinPortfolioItem()
  const unpin = useUnpinPortfolioItem()
  const remove = useDeletePortfolioItem()
  const invalidate = useInvalidatePortfolio(profile.username)
  const [error, setError] = useState<string | null>(null)

  const pinnedIds = new Set((profile.pins ?? []).map((item) => item.id))
  const items: PortfolioItem[] = listQuery.data?.data.items ?? []

  async function run(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
      await invalidate()
    } catch (err) {
      setError(mutationError(err))
    }
  }

  return (
    <div className="space-y-4">
      {listQuery.isLoading ? (
        <p className="text-sm text-muted-foreground">Loading portfolio…</p>
      ) : (
        <PortfolioGrid
          items={items}
          empty={<p className="text-sm text-muted-foreground">No portfolio items yet.</p>}
          renderActions={(item) => {
            const isPinned = pinnedIds.has(item.id)
            return (
              <>
                {item.visibility === "public" ? (
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={pin.isPending || unpin.isPending}
                    onClick={() =>
                      void run(() =>
                        isPinned
                          ? unpin.mutateAsync({ itemId: item.id })
                          : pin.mutateAsync({ data: { portfolioItemId: item.id } })
                      )
                    }
                  >
                    {isPinned ? "Unpin" : "Pin"}
                  </Button>
                ) : null}
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={remove.isPending}
                  onClick={() => void run(() => remove.mutateAsync({ itemId: item.id }))}
                >
                  Delete
                </Button>
              </>
            )
          }}
        />
      )}
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      <PortfolioAddForm profile={profile} />
    </div>
  )
}
