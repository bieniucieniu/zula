import { Redirect, Stack } from "expo-router"
import { useLogout } from "@zula/api/endpoints"
import { useState } from "react"
import { View } from "react-native"
import { Button } from "@/components/ui/button"
import { Text } from "@/components/ui/text"
import { useAuth, useInvalidateSession } from "@/lib/auth"
import { usePowerSync } from "@/lib/powersync"
import { clearTokens, getMemoryRefreshToken } from "@/lib/token-store"

export default function Screen() {
  const { session, ready } = useAuth()
  const invalidateSession = useInvalidateSession()
  const logout = useLogout()
  const { ready: syncReady } = usePowerSync()
  const [pending, setPending] = useState(false)

  if (!ready) {
    return (
      <View className="flex-1 items-center justify-center p-6">
        <Text className="text-muted-foreground text-sm">Loading…</Text>
      </View>
    )
  }

  if (!session) {
    return <Redirect href="/login" />
  }

  async function onLogout() {
    setPending(true)
    try {
      const refreshToken = getMemoryRefreshToken()
      await logout.mutateAsync({
        data: refreshToken ? { refreshToken } : undefined,
      })
    } catch {
      // still clear local tokens
    } finally {
      await clearTokens()
      await invalidateSession()
      setPending(false)
    }
  }

  return (
    <>
      <Stack.Screen options={{ title: "Zula", headerTransparent: true }} />
      <View className="flex-1 items-center justify-center gap-4 p-6">
        <Text className="text-lg font-medium">Signed in</Text>
        <Text className="text-muted-foreground text-sm">{session.email}</Text>
        <Text className="text-muted-foreground text-xs">
          PowerSync: {syncReady ? "connected" : session ? "connecting…" : "local only (no JWT)"}
        </Text>
        <Button disabled={pending} variant="outline" onPress={() => void onLogout()}>
          <Text>{pending ? "Logging out…" : "Logout"}</Text>
        </Button>
      </View>
    </>
  )
}
