import { Stack, useRouter } from "expo-router"
import { useLogout } from "@zula/api/endpoints"
import { useEffect, useState } from "react"
import { View } from "react-native"
import { Button } from "@/components/ui/button"
import { Text } from "@/components/ui/text"
import { useAuth } from "@/lib/auth"
import { clearStoredSession } from "@/lib/session-storage"

export default function Screen() {
  const { session, ready, refresh } = useAuth()
  const logout = useLogout()
  const router = useRouter()
  const [pending, setPending] = useState(false)

  useEffect(() => {
    if (ready && !session) {
      router.replace("/login")
    }
  }, [ready, session, router])

  if (!ready || !session) {
    return (
      <View className="flex-1 items-center justify-center p-6">
        <Text className="text-sm text-muted-foreground">Loading…</Text>
      </View>
    )
  }

  async function onLogout() {
    setPending(true)
    try {
      await logout.mutateAsync({})
    } catch {
      // still clear local session
    } finally {
      await clearStoredSession()
      await refresh()
      setPending(false)
      router.replace("/login")
    }
  }

  return (
    <>
      <Stack.Screen options={{ title: "Zula" }} />
      <View className="flex-1 items-center justify-center gap-4 p-6">
        <View className="max-w-sm flex-col items-center gap-2">
          <Text className="text-lg font-medium">Signed in</Text>
          <Text className="text-center text-sm text-muted-foreground">{session.email}</Text>
          <Button variant="outline" disabled={pending} onPress={() => void onLogout()}>
            <Text>{pending ? "Logging out…" : "Logout"}</Text>
          </Button>
        </View>
      </View>
    </>
  )
}
