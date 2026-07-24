let apiBaseUrl: string | ((url: string, options: RequestInit) => string) = ""
let accessToken: string | null = null
let authMode: "cookie" | "bearer" = "cookie"
let unauthorizedHandler: (() => Promise<boolean>) | null = null

export function setUnauthorizedHandler(handler: (() => Promise<boolean>) | null) {
  unauthorizedHandler = handler
}

export function getApiBaseUrl(url: string, options: RequestInit): string {
  if (typeof apiBaseUrl === "function") {
    return apiBaseUrl(url, options)
  }
  if (typeof apiBaseUrl === "string") {
    return `${apiBaseUrl}${url}`
  }
  return url
}

export function setApiBaseUrl(url: string): void
export function setApiBaseUrl(url: (url: string, options: RequestInit) => string): void
export function setApiBaseUrl(url: string | ((url: string, options: RequestInit) => string)) {
  if (typeof url === "string") {
    url = url.replace(/[/\\]+$/, "")
  }
  apiBaseUrl = url
}

export function setApiAuthMode(mode: "cookie" | "bearer") {
  authMode = mode
}

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export const customInstance = async <T>(url: string, options: RequestInit): Promise<T> => {
  return executeRequest<T>(url, options, false)
}

async function executeRequest<T>(url: string, options: RequestInit, isRetry: boolean): Promise<T> {
  const headers = new Headers(options.headers)
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
  }
  if (authMode === "bearer" && accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`)
  }

  const res = await fetch(`${getApiBaseUrl(url, options)}`, {
    ...options,
    credentials: authMode === "cookie" ? "include" : "omit",
    headers,
  })

  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined

  if (res.status === 401 && !isRetry && unauthorizedHandler) {
    const refreshed = await unauthorizedHandler()
    if (refreshed) {
      return executeRequest<T>(url, options, true)
    }
  }

  if (!res.ok) {
    throw data ?? new Error(res.statusText)
  }

  return { data, status: res.status, headers: res.headers } as T
}

export type BodyType<BodyData> = BodyData
