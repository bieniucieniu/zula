export type ApiAuthMode = "cookie" | "bearer"

export type ApiClientOptions = {
  baseUrl?: string | ((url: string, options: RequestInit) => string)
  authMode?: ApiAuthMode
  getAccessToken?: () => string | null
  setAccessToken?: (token: string | null) => void
  onUnauthorized?: (() => Promise<boolean>) | null
}

export type ApiClient = {
  customInstance: <T>(url: string, options: RequestInit) => Promise<T>
  getAccessToken: () => string | null
  setAccessToken: (token: string | null) => void
  setUnauthorizedHandler: (handler: (() => Promise<boolean>) | null) => void
}

function resolveBaseUrl(
  baseUrl: ApiClientOptions["baseUrl"],
  url: string,
  options: RequestInit
): string {
  if (typeof baseUrl === "function") {
    return baseUrl(url, options)
  }
  if (typeof baseUrl === "string" && baseUrl.length > 0) {
    return `${baseUrl.replace(/[/\\]+$/, "")}${url}`
  }
  return url
}

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  let accessToken: string | null = null
  let unauthorizedHandler: (() => Promise<boolean>) | null = options.onUnauthorized ?? null
  const authMode: ApiAuthMode = options.authMode ?? "cookie"
  const getAccessToken = options.getAccessToken ?? (() => accessToken)
  const setAccessTokenFn =
    options.setAccessToken ??
    ((token: string | null) => {
      accessToken = token
    })

  async function executeRequest<T>(
    url: string,
    requestOptions: RequestInit,
    isRetry: boolean
  ): Promise<T> {
    const headers = new Headers(requestOptions.headers)
    if (!headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json")
    }
    const token = getAccessToken()
    if (authMode === "bearer" && token) {
      headers.set("Authorization", `Bearer ${token}`)
    }

    const res = await fetch(resolveBaseUrl(options.baseUrl, url, requestOptions), {
      ...requestOptions,
      credentials: authMode === "cookie" ? "include" : "omit",
      headers,
    })

    const text = await res.text()
    let data: unknown
    try {
      data = text ? JSON.parse(text) : undefined
    } catch {
      data = text || undefined
    }

    if (res.status === 401 && !isRetry && unauthorizedHandler) {
      const refreshed = await unauthorizedHandler()
      if (refreshed) {
        return executeRequest<T>(url, requestOptions, true)
      }
    }

    if (!res.ok) {
      throw data ?? new Error(res.statusText)
    }

    return { data, status: res.status, headers: res.headers } as T
  }

  return {
    customInstance: <T>(url: string, requestOptions: RequestInit) =>
      executeRequest<T>(url, requestOptions, false),
    getAccessToken,
    setAccessToken: setAccessTokenFn,
    setUnauthorizedHandler: (handler) => {
      unauthorizedHandler = handler
    },
  }
}

let defaultClient: ApiClient | null = null

export function setDefaultApiClient(client: ApiClient) {
  defaultClient = client
}

export function getDefaultApiClient(): ApiClient {
  if (!defaultClient) {
    throw new Error(
      "API client not configured; call setDefaultApiClient(createApiClient(...)) at app boot"
    )
  }
  return defaultClient
}

/** Orval mutator — delegates to the default client set at boot. */
export const customInstance = async <T>(url: string, options: RequestInit): Promise<T> => {
  return getDefaultApiClient().customInstance<T>(url, options)
}

export type BodyType<BodyData> = BodyData

export function setUnauthorizedHandler(handler: (() => Promise<boolean>) | null) {
  getDefaultApiClient().setUnauthorizedHandler(handler)
}

export function setAccessToken(token: string | null) {
  getDefaultApiClient().setAccessToken(token)
}

export function getAccessToken() {
  return getDefaultApiClient().getAccessToken()
}
