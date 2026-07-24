export type DevAuthConfig = {
  secret: string
  email: string
}

export function getDevAuthConfig(): DevAuthConfig | null {
  const secret = process.env.EXPO_PUBLIC_DEV_AUTH_SECRET?.trim()
  if (!secret) return null

  return {
    secret,
    email: process.env.EXPO_PUBLIC_DEV_AUTH_EMAIL?.trim() || "dev@zula.local",
  }
}
