import { createFileRoute } from "@tanstack/react-router"
import { TradeDetail } from "@/features/trade/trade-detail"

export const Route = createFileRoute("/trades/$id")({
  component: TradeDetailPage,
  head: () => ({
    meta: [{ title: "Trade · Zula" }],
  }),
})

function TradeDetailPage() {
  const { id } = Route.useParams()
  return <TradeDetail id={id} />
}
