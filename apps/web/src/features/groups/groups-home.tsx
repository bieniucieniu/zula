import { Link, useNavigate } from "@tanstack/react-router"
import { useState } from "react"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"

export function GroupsHome() {
  const navigate = useNavigate()
  const [slug, setSlug] = useState("")

  function onOpen(e: React.FormEvent) {
    e.preventDefault()
    const value = slug.trim()
    if (!value) return
    void navigate({ to: "/groups/$idOrSlug", params: { idOrSlug: value } })
  }

  return (
    <div className="space-y-4 text-sm">
      <div className="flex items-center justify-between gap-2">
        <h1 className="font-heading text-lg font-medium">Groups</h1>
        <Link to="/groups/new" className={cn(buttonVariants({ size: "sm" }))}>
          Create
        </Link>
      </div>

      <form className="space-y-3" onSubmit={onOpen}>
        <div className="space-y-1.5">
          <Label htmlFor="slug">Open by slug</Label>
          <Input
            id="slug"
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
            placeholder="group-slug"
          />
        </div>
        <Button type="submit" size="sm" disabled={!slug.trim()}>
          Open
        </Button>
      </form>
    </div>
  )
}
