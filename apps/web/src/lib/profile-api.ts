import { getDefaultApiClient } from "@zula/api"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

export type SellerActivityCounts = {
  activeOffers: number
  activeNeeds: number
  activeTrips: number
  fulfilledItems: number
  completedTrades?: number
}

export type SellerReviewPreview = {
  id: string
  reviewerUsername: string
  rating: number
  comment?: string | null
  createdAt: string
}

export type SellerProfileResponse = {
  userId: string
  username: string
  displayName?: string | null
  avatarUrl?: string | null
  bio?: string | null
  sellerHeadline?: string | null
  locationTag?: string | null
  memberSince: string
  explicitRatingAvg: number
  implicitTrustScore: number
  ratingCount: number
  activity?: SellerActivityCounts | null
  recentReviews?: SellerReviewPreview[]
  viewerHasBlocked?: boolean | null
  viewerIsBlocked?: boolean | null
}

export type MyProfileResponse = {
  public: SellerProfileResponse
  timezone?: string | null
  preferredLanguage?: string | null
  email?: string | null
  isAdmin?: boolean
}

export type UpdateMyProfileRequest = {
  displayName?: string | null
  bio?: string | null
  avatarUrl?: string | null
  sellerHeadline?: string | null
  locationTag?: string | null
  timezone?: string | null
  preferredLanguage?: string | null
}

type ApiSuccess<T> = { data: T; status: number }

function api() {
  return getDefaultApiClient()
}

export async function getMyProfile() {
  return api().customInstance<ApiSuccess<MyProfileResponse>>("/api/users/me/profile", {
    method: "GET",
  })
}

export async function updateMyProfile(body: UpdateMyProfileRequest) {
  return api().customInstance<ApiSuccess<MyProfileResponse>>("/api/users/me/profile", {
    method: "PATCH",
    body: JSON.stringify(body),
  })
}

export async function getSellerProfile(idOrUsername: string) {
  return api().customInstance<ApiSuccess<SellerProfileResponse>>(
    `/api/sellers/${encodeURIComponent(idOrUsername)}`,
    { method: "GET" },
  )
}

export async function blockUser(userId: string) {
  return api().customInstance<ApiSuccess<{ blockedUserId: string }>>(
    `/api/users/${encodeURIComponent(userId)}/block`,
    { method: "POST" },
  )
}

export async function unblockUser(userId: string) {
  return api().customInstance<ApiSuccess<{ unblockedUserId: string }>>(
    `/api/users/${encodeURIComponent(userId)}/block`,
    { method: "DELETE" },
  )
}

export const myProfileQueryKey = ["profile", "me"] as const
export const sellerProfileQueryKey = (idOrUsername: string) =>
  ["profile", "seller", idOrUsername] as const

export function useMyProfile(enabled = true) {
  return useQuery({
    queryKey: myProfileQueryKey,
    queryFn: getMyProfile,
    enabled,
    retry: false,
  })
}

export function useSellerProfile(idOrUsername: string, enabled = true) {
  return useQuery({
    queryKey: sellerProfileQueryKey(idOrUsername),
    queryFn: () => getSellerProfile(idOrUsername),
    enabled: enabled && idOrUsername.length > 0,
    retry: false,
  })
}

export function useUpdateMyProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updateMyProfile,
    onSuccess: async (result) => {
      queryClient.setQueryData(myProfileQueryKey, result)
      const username = result.data.public.username
      await queryClient.invalidateQueries({ queryKey: sellerProfileQueryKey(username) })
      await queryClient.invalidateQueries({
        queryKey: sellerProfileQueryKey(result.data.public.userId),
      })
    },
  })
}

export function useBlockUser(idOrUsername: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: blockUser,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: sellerProfileQueryKey(idOrUsername) })
    },
  })
}

export function useUnblockUser(idOrUsername: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: unblockUser,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: sellerProfileQueryKey(idOrUsername) })
    },
  })
}
