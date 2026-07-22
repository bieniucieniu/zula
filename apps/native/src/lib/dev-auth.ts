const defaultDevEmail = "dev@zula.local"

export function getDevAuthSecret(): string | undefined {
  const value = process.env.EXPO_PUBLIC_DEV_AUTH_SECRET?.trim()
  return value ? value : undefined
}

export function getDevAuthEmail(): string {
  return process.env.EXPO_PUBLIC_DEV_AUTH_EMAIL?.trim() || defaultDevEmail
}

export function isDevAuthEnabled(): boolean {
  return __DEV__ && Boolean(getDevAuthSecret())
}
