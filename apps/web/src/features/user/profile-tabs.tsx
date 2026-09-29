import type { PortfolioItem, SellerProfileResponse } from "@zula/api"
import { useListFeedByAuthor, useListPortfolioItems } from "@zula/api/endpoints"
import { Link } from "@tanstack/react-router"
import { FeedItemCard } from "@/features/feed/feed-item-card"
import { PortfolioGrid, SellerPins } from "@/features/user/portfolio-view"
import { RecentReviews, SellerBio } from "@/features/user/seller-profile-view"
import { buttonVariants } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { cn } from "@/lib/utils"

type ProfileTabsProps = {
  profile: SellerProfileResponse
  /** Public seller list uses user id; own profile uses "me". */
  id: string
  /** Show Add item on the portfolio tab (own profile). */
  canManagePortfolio?: boolean
}

export function ProfileContentTabs({ profile, id, canManagePortfolio = false }: ProfileTabsProps) {
  const portfolioQuery = useListPortfolioItems(id)
  const portfolioItems: PortfolioItem[] = portfolioQuery.data?.data.items ?? []
  const listingsQuery = useListFeedByAuthor(profile.userId)
  const listings = listingsQuery.data?.data.items ?? []

  return (
    <Tabs defaultValue="about" className="w-full gap-4">
      <TabsList variant="line" className="w-full justify-start">
        <TabsTrigger value="about">About</TabsTrigger>
        <TabsTrigger value="portfolio">Portfolio</TabsTrigger>
        <TabsTrigger value="listings">Listings</TabsTrigger>
        <TabsTrigger value="reviews">Reviews</TabsTrigger>
      </TabsList>

      <TabsContent value="about" className="space-y-4 text-sm">
        <SellerBio profile={profile} />
        <div className="space-y-2">
          <h3 className="font-heading text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Pinned
          </h3>
          <SellerPins profile={profile} />
        </div>
      </TabsContent>

      <TabsContent value="portfolio" className="space-y-4 text-sm">
        {canManagePortfolio ? (
          <div className="flex justify-end">
            <Link
              to="/profile/portfolio"
              className={cn(buttonVariants({ variant: "outline", size: "sm" }))}
            >
              Add item
            </Link>
          </div>
        ) : null}
        {portfolioQuery.isLoading ? (
          <p className="text-muted-foreground">Loading portfolio…</p>
        ) : (
          <PortfolioGrid
            items={portfolioItems}
            empty={
              <div className="flex flex-col items-center gap-3 py-10 text-center">
                <p className="text-muted-foreground">No public portfolio items.</p>
                {canManagePortfolio ? (
                  <Link
                    to="/profile/portfolio"
                    className={cn(buttonVariants({ variant: "default", size: "sm" }))}
                  >
                    Add item
                  </Link>
                ) : null}
              </div>
            }
          />
        )}
      </TabsContent>

      <TabsContent value="listings" className="space-y-4 text-sm">
        {listingsQuery.isLoading ? (
          <p className="text-muted-foreground">Loading listings…</p>
        ) : listings.length === 0 ? (
          <p className="text-muted-foreground">No listings yet.</p>
        ) : (
          <div className="divide-y rounded-md border px-3">
            {listings.map((item) => (
              <FeedItemCard key={item.id} item={item} />
            ))}
          </div>
        )}
      </TabsContent>

      <TabsContent value="reviews" className="text-sm">
        <RecentReviews profile={profile} />
      </TabsContent>
    </Tabs>
  )
}
