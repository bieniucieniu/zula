import { listProviders, type listProvidersResponse } from "@zula/api/endpoints"
import { useQuery, type UseQueryOptions } from "@tanstack/react-query"
import type { OAuthProviderInfo } from "../types"
import { getOAuthProvidersQueryKey } from "./query-keys"

type OAuthProvidersQueryOptions<TData = OAuthProviderInfo[]> = {
  query?: Partial<UseQueryOptions<listProvidersResponse, unknown, TData>>
}

export function useOAuthProviders<TData = OAuthProviderInfo[]>(
  options?: OAuthProvidersQueryOptions<TData>
) {
  return useQuery({
    queryKey: getOAuthProvidersQueryKey(),
    queryFn: ({ signal }) => listProviders({ signal }),
    select: (response) => response.data.providers as TData,
    staleTime: 60 * 60 * 1000,
    ...options?.query,
  })
}

export function useOAuthProvider(providerId: string) {
  const query = useOAuthProviders()
  const provider = query.data?.find((item) => item.id === providerId)

  return {
    ...query,
    provider,
    ready: Boolean(provider?.clientId),
  }
}
