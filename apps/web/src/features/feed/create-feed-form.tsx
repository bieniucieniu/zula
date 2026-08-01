import { uploadImageObject } from "@zula/api"
import { useCreateFeedItem, useListTraits } from "@zula/api/endpoints"
import type { TraitNode } from "@zula/api"
import { useNavigate } from "@tanstack/react-router"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"
import { Textarea } from "@/components/ui/textarea"

function flattenTraits(nodes: TraitNode[]): TraitNode[] {
  const out: TraitNode[] = []
  for (const node of nodes) {
    out.push(node)
    if (node.children?.length) out.push(...flattenTraits(node.children))
  }
  return out
}

export function CreateFeedForm({ groupId }: { groupId?: string }) {
  const navigate = useNavigate()
  const create = useCreateFeedItem()
  const traitsQuery = useListTraits()
  const traits = flattenTraits(traitsQuery.data?.data.traits ?? [])

  const [kind, setKind] = useState("offer")
  const [title, setTitle] = useState("")
  const [body, setBody] = useState("")
  const [traitIds, setTraitIds] = useState<string[]>([])
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  function toggleTrait(id: string, checked: boolean) {
    setTraitIds((prev) => (checked ? [...prev, id] : prev.filter((t) => t !== id)))
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setPending(true)
    try {
      let mediaObjectKeys: string[] | undefined
      if (file) {
        const uploaded = await uploadImageObject(file)
        mediaObjectKeys = [uploaded.objectKey]
      }
      const res = await create.mutateAsync({
        data: {
          kind,
          title,
          body: body || null,
          traitIds,
          mediaObjectKeys,
          groupId: groupId ?? null,
        },
      })
      const id = res.data.id
      await navigate({ to: "/feed/items/$id", params: { id } })
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create feed item")
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="space-y-4 text-sm" onSubmit={(e) => void onSubmit(e)}>
      <h1 className="font-heading text-lg font-medium">New feed item</h1>
      {groupId ? <p className="text-xs text-muted-foreground">Group: {groupId}</p> : null}

      <div className="space-y-1.5">
        <Label htmlFor="kind">Kind</Label>
        <NativeSelect id="kind" value={kind} onChange={(e) => setKind(e.target.value)}>
          <NativeSelectOption value="need">need</NativeSelectOption>
          <NativeSelectOption value="offer">offer</NativeSelectOption>
          <NativeSelectOption value="trip">trip</NativeSelectOption>
        </NativeSelect>
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="title">Title</Label>
        <Input id="title" required value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="body">Body</Label>
        <Textarea id="body" value={body} onChange={(e) => setBody(e.target.value)} />
      </div>

      <div className="space-y-2">
        <Label>Traits</Label>
        {traitsQuery.isLoading ? (
          <p className="text-xs text-muted-foreground">Loading traits…</p>
        ) : traits.length === 0 ? (
          <p className="text-xs text-muted-foreground">No traits.</p>
        ) : (
          <div className="space-y-2">
            {traits.map((trait) => (
              <label key={trait.id} className="flex items-center gap-2 text-xs">
                <Checkbox
                  checked={traitIds.includes(trait.id)}
                  onCheckedChange={(checked) => toggleTrait(trait.id, checked === true)}
                />
                {trait.label}
              </label>
            ))}
          </div>
        )}
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="media">Image (optional)</Label>
        <Input
          id="media"
          type="file"
          accept="image/*"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
      </div>

      {error ? <p className="text-sm text-destructive">{error}</p> : null}

      <Button type="submit" disabled={pending || !title.trim()}>
        {pending ? "Creating…" : "Create"}
      </Button>
    </form>
  )
}
