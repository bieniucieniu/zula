import { Link } from "@tanstack/react-router"
import type { SellerProfileResponse } from "@zula/api"
import { cn } from "@/lib/utils"

function initials(profile: SellerProfileResponse) {
  const source = profile.displayName?.trim() || profile.username
  return source.slice(0, 2).toUpperCase()
}

function formatMemberSince(iso: string) {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString(undefined, { year: "numeric", month: "short" })
}

export function ProfileAvatar({
  profile,
  className,
}: {
  profile: SellerProfileResponse
  className?: string
}) {
  if (profile.avatarUrl) {
    return (
      <img
        src={profile.avatarUrl}
        alt=""
        className={cn("size-20 object-cover bg-muted", className)}
      />
    )
  }
  return (
    <div
      className={cn(
        "flex size-20 items-center justify-center bg-muted font-heading text-lg font-medium text-muted-foreground",
        className
      )}
      aria-hidden
    >
      {initials(profile)}
    </div>
  )
}

export function SellerProfileHeader({
  profile,
  actions,
}: {
  profile: SellerProfileResponse
  actions?: React.ReactNode
}) {
  return (
    <header className="flex flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex gap-4">
        <ProfileAvatar profile={profile} />
        <div className="min-w-0 space-y-1">
          <h1 className="font-heading text-2xl font-medium tracking-tight">
            {profile.displayName?.trim() || profile.username}
          </h1>
          <p className="text-sm text-muted-foreground">@{profile.username}</p>
          {profile.sellerHeadline ? (
            <p className="max-w-xl text-sm text-foreground/80">{profile.sellerHeadline}</p>
          ) : null}
          <p className="text-xs text-muted-foreground">
            Member since {formatMemberSince(profile.memberSince)}
            {profile.locationTag ? ` · ${profile.locationTag}` : ""}
          </p>
        </div>
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
    </header>
  )
}

export function SellerTrustRow({ profile }: { profile: SellerProfileResponse }) {
  return (
    <dl className="grid grid-cols-3 gap-4 border-y py-4 text-sm">
      <div>
        <dt className="text-muted-foreground">Rating</dt>
        <dd className="font-medium tabular-nums">
          {profile.explicitRatingAvg.toFixed(2)}
          <span className="ml-1 text-xs font-normal text-muted-foreground">
            ({profile.ratingCount})
          </span>
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">Trust</dt>
        <dd className="font-medium tabular-nums">{profile.implicitTrustScore}</dd>
      </div>
      <div>
        <dt className="text-muted-foreground">Activity</dt>
        <dd className="font-medium tabular-nums">
          {profile.activity
            ? profile.activity.activeOffers +
              profile.activity.activeNeeds +
              profile.activity.activeTrips
            : "—"}
        </dd>
      </div>
    </dl>
  )
}

export function SellerBio({ profile }: { profile: SellerProfileResponse }) {
  if (!profile.bio?.trim()) {
    return <p className="text-sm text-muted-foreground">No bio yet.</p>
  }
  return <p className="max-w-2xl whitespace-pre-wrap text-sm leading-relaxed">{profile.bio}</p>
}

export function RecentReviews({ profile }: { profile: SellerProfileResponse }) {
  const reviews = profile.recentReviews ?? []
  if (reviews.length === 0) {
    return <p className="text-sm text-muted-foreground">No reviews yet.</p>
  }
  return (
    <ul className="divide-y border-t">
      {reviews.map((review) => (
        <li key={review.id} className="space-y-1 py-3">
          <div className="flex items-baseline justify-between gap-2 text-sm">
            <span className="font-medium">@{review.reviewerUsername}</span>
            <span className="tabular-nums text-muted-foreground">{review.rating}/5</span>
          </div>
          {review.comment ? <p className="text-sm text-foreground/80">{review.comment}</p> : null}
        </li>
      ))}
    </ul>
  )
}

export function ProfileShell({ children, title }: { children: React.ReactNode; title?: string }) {
  return (
    <div className="min-h-svh bg-background">
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-8 px-6 py-10">
        <nav className="flex items-center justify-between text-sm">
          <Link to="/" className="text-muted-foreground transition-colors hover:text-foreground">
            ← Home
          </Link>
          {title ? <span className="text-muted-foreground">{title}</span> : null}
        </nav>
        {children}
      </div>
    </div>
  )
}
