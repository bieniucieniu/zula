import type { PortfolioItem, SellerProfileResponse } from "@zula/api"
import { cn } from "@/lib/utils"

function coverLabel(item: PortfolioItem) {
  return item.title.trim().slice(0, 2).toUpperCase() || "?"
}

export function PortfolioCover({ item, className }: { item: PortfolioItem; className?: string }) {
  if (item.coverUrl) {
    return <img src={item.coverUrl} alt="" className={cn("size-full object-cover", className)} />
  }
  return (
    <div
      className={cn(
        "flex size-full items-center justify-center bg-muted font-heading text-2xl font-medium text-muted-foreground",
        className
      )}
      aria-hidden
    >
      {coverLabel(item)}
    </div>
  )
}

export function PortfolioItemCard({
  item,
  actions,
  className,
}: {
  item: PortfolioItem
  actions?: React.ReactNode
  className?: string
}) {
  return (
    <li className={cn("group flex flex-col gap-2", className)}>
      <div className="relative aspect-[4/3] overflow-hidden bg-muted">
        <PortfolioCover item={item} />
        {actions ? (
          <div className="absolute inset-x-0 bottom-0 flex flex-wrap gap-1 bg-gradient-to-t from-background/90 to-transparent p-2 opacity-100 sm:opacity-0 sm:transition-opacity sm:group-hover:opacity-100">
            {actions}
          </div>
        ) : null}
        {item.visibility === "unlisted" ? (
          <span className="absolute left-2 top-2 bg-background/90 px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">
            unlisted
          </span>
        ) : null}
      </div>
      <div className="min-w-0 space-y-0.5">
        <div className="flex flex-wrap items-baseline gap-2">
          <p className="truncate text-sm font-medium">{item.title}</p>
          <span className="shrink-0 text-[10px] uppercase tracking-wide text-muted-foreground">
            {item.kind}
          </span>
        </div>
        {item.summary ? (
          <p className="line-clamp-2 text-xs text-foreground/80">{item.summary}</p>
        ) : null}
        {item.externalUrl ? (
          <a
            href={item.externalUrl}
            target="_blank"
            rel="noreferrer"
            className="block truncate text-xs text-muted-foreground underline-offset-2 hover:underline"
          >
            {item.externalUrl}
          </a>
        ) : null}
      </div>
    </li>
  )
}

export function PortfolioGrid({
  items,
  empty,
  renderActions,
}: {
  items: PortfolioItem[]
  empty?: React.ReactNode
  renderActions?: (item: PortfolioItem) => React.ReactNode
}) {
  if (items.length === 0) {
    return <>{empty ?? <p className="text-sm text-muted-foreground">No portfolio items.</p>}</>
  }
  return (
    <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3">
      {items.map((item) => (
        <PortfolioItemCard key={item.id} item={item} actions={renderActions?.(item)} />
      ))}
    </ul>
  )
}

export function SellerPins({ profile }: { profile: SellerProfileResponse }) {
  const pins = profile.pins ?? []
  if (pins.length === 0) {
    return <p className="text-sm text-muted-foreground">No pinned work.</p>
  }
  return <PortfolioGrid items={pins} />
}
