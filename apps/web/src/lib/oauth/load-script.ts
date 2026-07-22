const loadedScripts = new Map<string, Promise<void>>()

export type LoadScriptOptions = {
  referrerPolicy?: ReferrerPolicy
}

export function loadScript(src: string, options?: LoadScriptOptions): Promise<void> {
  const existing = loadedScripts.get(src)
  if (existing) return existing

  const promise = new Promise<void>((resolve, reject) => {
    const script = document.createElement("script")
    script.src = src
    script.async = true
    script.defer = true
    if (options?.referrerPolicy) {
      script.referrerPolicy = options.referrerPolicy
    }
    script.onload = () => resolve()
    script.onerror = () => reject(new Error(`Failed to load script: ${src}`))
    document.head.appendChild(script)
  })

  loadedScripts.set(src, promise)
  return promise
}
