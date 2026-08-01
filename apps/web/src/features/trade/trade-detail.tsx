import {
  getGetTradeQueryKey,
  useAcceptTrade,
  useCancelTrade,
  useCompleteTrade,
  useConfirmMeetup,
  useGetChatRoomByTrade,
  useGetTrade,
  useProposeMeetup,
  useSetTradeFulfillmentPlace,
  useSetTradeLocationMode,
} from "@zula/api/endpoints"
import { Link } from "@tanstack/react-router"
import { useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select"
import { cn } from "@/lib/utils"

export function TradeDetail({ id }: { id: string }) {
  const queryClient = useQueryClient()
  const tradeQuery = useGetTrade(id)
  const chatQuery = useGetChatRoomByTrade(id)
  const accept = useAcceptTrade()
  const cancel = useCancelTrade()
  const complete = useCompleteTrade()
  const setLocationMode = useSetTradeLocationMode()
  const setPlace = useSetTradeFulfillmentPlace()
  const proposeMeetup = useProposeMeetup()
  const confirmMeetup = useConfirmMeetup()

  const [locationMode, setLocationModeLocal] = useState("provider")
  const [placeNote, setPlaceNote] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const trade = tradeQuery.data?.data
  const chatRoom = chatQuery.data?.data

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: getGetTradeQueryKey(id) })
  }

  async function run(action: () => Promise<unknown>) {
    setPending(true)
    setError(null)
    try {
      await action()
      await refresh()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action failed")
    } finally {
      setPending(false)
    }
  }

  if (tradeQuery.isLoading) {
    return <p className="text-sm text-muted-foreground">Loading trade…</p>
  }

  if (tradeQuery.isError || !trade) {
    return (
      <div className="space-y-2 text-sm">
        <p className="text-destructive">Could not load trade.</p>
        <Button variant="outline" size="sm" onClick={() => void tradeQuery.refetch()}>
          Retry
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-6 text-sm">
      <div className="space-y-1">
        <h1 className="font-heading text-lg font-medium">Trade</h1>
        <p className="text-xs text-muted-foreground">
          {trade.template} · {trade.status}
          {trade.locationMode ? ` · ${trade.locationMode}` : ""}
        </p>
        <p className="text-xs text-muted-foreground">
          {trade.initiatorId} ↔ {trade.counterpartyId}
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={pending}
          onClick={() => void run(() => accept.mutateAsync({ id }))}
        >
          Accept
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={pending}
          onClick={() => void run(() => cancel.mutateAsync({ id }))}
        >
          Cancel
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={pending}
          onClick={() => void run(() => complete.mutateAsync({ id }))}
        >
          Complete
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={pending}
          onClick={() => void run(() => proposeMeetup.mutateAsync({ id }))}
        >
          Propose meetup
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={pending}
          onClick={() => void run(() => confirmMeetup.mutateAsync({ id }))}
        >
          Confirm meetup
        </Button>
        {chatRoom?.id ? (
          <Link
            to="/chat/rooms/$id"
            params={{ id: chatRoom.id }}
            className={cn(buttonVariants({ size: "sm" }))}
          >
            Open chat
          </Link>
        ) : null}
      </div>

      <section className="space-y-2">
        <h2 className="font-heading text-sm font-medium">Location mode</h2>
        <div className="flex flex-wrap items-end gap-2">
          <div className="space-y-1.5">
            <Label htmlFor="locationMode">Mode</Label>
            <NativeSelect
              id="locationMode"
              value={locationMode}
              onChange={(e) => setLocationModeLocal(e.target.value)}
            >
              <NativeSelectOption value="provider">provider</NativeSelectOption>
              <NativeSelectOption value="client">client</NativeSelectOption>
              <NativeSelectOption value="negotiated">negotiated</NativeSelectOption>
            </NativeSelect>
          </div>
          <Button
            size="sm"
            disabled={pending}
            onClick={() =>
              void run(() => setLocationMode.mutateAsync({ id, data: { locationMode } }))
            }
          >
            Set mode
          </Button>
        </div>
      </section>

      <section className="space-y-2">
        <h2 className="font-heading text-sm font-medium">Place note</h2>
        {trade.location?.placeNote ? (
          <p className="text-xs text-muted-foreground">Current: {trade.location.placeNote}</p>
        ) : null}
        <div className="flex gap-2">
          <Input
            value={placeNote}
            onChange={(e) => setPlaceNote(e.target.value)}
            placeholder="Place note"
          />
          <Button
            size="sm"
            disabled={pending || !placeNote.trim()}
            onClick={() =>
              void run(() => setPlace.mutateAsync({ id, data: { placeNote: placeNote.trim() } }))
            }
          >
            Save
          </Button>
        </div>
      </section>

      {error ? <p className="text-destructive">{error}</p> : null}
    </div>
  )
}
