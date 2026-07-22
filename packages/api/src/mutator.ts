let apiBaseUrl: string | ((url: string, options: RequestInit) => string) = ""

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
    url = url.replaceAll(/(\\|\/)$/, "")
  }
  apiBaseUrl = url
}

export const customInstance = async <T>(url: string, options: RequestInit): Promise<T> => {
  const res = await fetch(`${getApiBaseUrl(url, options)}`, {
    ...options,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  })

  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined

  if (!res.ok) {
    throw data ?? new Error(res.statusText)
  }

  return data as T
}
