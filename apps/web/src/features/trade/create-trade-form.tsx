import { useCreateTrade } from "@zula/api/endpoints"
import { useNavigate } from "@tanstack/react-router"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"

export function CreateTradeForm({
  feedItemId,
  counterpartyId,
}: {
  feedItemId?: string
  counterpartyId?: string
}) {
  const navigate = useNavigate()
  const create = useCreateTrade()
  const [template, setTemplate] = useState("swap")
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!counterpartyId) {
      setError("counterpartyId required")
      return
    }
    setError(null)
    setPending(true)
    try {
      const res = await create.mutateAsync({
        data: {
          counterpartyId,
          template,
          feedItemId: feedItemId ?? null,
        },
      })
      await navigate({ to: "/trades/$id", params: { id: res.data.id } })
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create trade")
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="space-y-4 text-sm" onSubmit={(e) => void onSubmit(e)}>
      <h1 className="font-heading text-lg font-medium">Start trade</h1>
      <p className="text-xs text-muted-foreground">
        Counterparty: {counterpartyId ?? "—"}
        {feedItemId ? ` · Feed item: ${feedItemId}` : ""}
      </p>

      <div className="space-y-1.5">
        <Label htmlFor="template">Template</Label>
        <NativeSelect id="template" value={template} onChange={(e) => setTemplate(e.target.value)}>
          <NativeSelectOption value="swap">swap</NativeSelectOption>
          <NativeSelectOption value="meetup_cash">meetup_cash</NativeSelectOption>
        </NativeSelect>
      </div>

      {error ? <p className="text-destructive">{error}</p> : null}

      <Button type="submit" disabled={pending || !counterpartyId}>
        {pending ? "Creating…" : "Create trade"}
      </Button>
    </form>
  )
}
