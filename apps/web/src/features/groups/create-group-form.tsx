import { useCreateGroup } from "@zula/api/endpoints"
import { useNavigate } from "@tanstack/react-router"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"
import { Textarea } from "@/components/ui/textarea"

export function CreateGroupForm() {
  const navigate = useNavigate()
  const create = useCreateGroup()
  const [slug, setSlug] = useState("")
  const [title, setTitle] = useState("")
  const [description, setDescription] = useState("")
  const [visibility, setVisibility] = useState("public")
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setPending(true)
    try {
      const res = await create.mutateAsync({
        data: {
          slug: slug.trim(),
          title: title.trim(),
          description: description.trim() || null,
          visibility,
        },
      })
      const idOrSlug = res.data.slug || res.data.id
      await navigate({ to: "/groups/$idOrSlug", params: { idOrSlug } })
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create group")
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="space-y-4 text-sm" onSubmit={(e) => void onSubmit(e)}>
      <h1 className="font-heading text-lg font-medium">Create group</h1>

      <div className="space-y-1.5">
        <Label htmlFor="slug">Slug</Label>
        <Input id="slug" required value={slug} onChange={(e) => setSlug(e.target.value)} />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="title">Title</Label>
        <Input id="title" required value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="description">Description</Label>
        <Textarea
          id="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="visibility">Visibility</Label>
        <NativeSelect
          id="visibility"
          value={visibility}
          onChange={(e) => setVisibility(e.target.value)}
        >
          <NativeSelectOption value="public">public</NativeSelectOption>
          <NativeSelectOption value="private">private</NativeSelectOption>
        </NativeSelect>
      </div>

      {error ? <p className="text-destructive">{error}</p> : null}

      <Button type="submit" disabled={pending || !slug.trim() || !title.trim()}>
        {pending ? "Creating…" : "Create"}
      </Button>
    </form>
  )
}
