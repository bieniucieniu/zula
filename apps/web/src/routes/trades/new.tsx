import { createFileRoute } from "@tanstack/react-router"
import { CreateTradeForm } from "@/features/trade/create-trade-form"

type TradeNewSearch = {
  feedItemId?: string
  counterpartyId?: string
}

export const Route = createFileRoute("/trades/new")({
  validateSearch: (search: Record<string, unknown>): TradeNewSearch => ({
    feedItemId: typeof search.feedItemId === "string" ? search.feedItemId : undefined,
    counterpartyId: typeof search.counterpartyId === "string" ? search.counterpartyId : undefined,
  }),
  component: TradeNewPage,
  head: () => ({
    meta: [{ title: "New trade · Zula" }],
  }),
})

function TradeNewPage() {
  const { feedItemId, counterpartyId } = Route.useSearch()
  return <CreateTradeForm feedItemId={feedItemId} counterpartyId={counterpartyId} />
}
