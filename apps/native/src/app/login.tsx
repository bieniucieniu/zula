import { Stack, useRouter } from "expo-router"
import { useEffect } from "react"
import { View } from "react-native"
import { LoginForm } from "@/components/login-form"
import { Text } from "@/components/ui/text"
import { useAuth } from "@/lib/auth"

export default function LoginScreen() {
  const { session, ready } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (ready && session) {
      router.replace("/")
    }
  }, [ready, session, router])

  if (!ready || session) {
    return (
      <View className="flex-1 items-center justify-center p-6">
        <Text className="text-sm text-muted-foreground">Loading…</Text>
      </View>
    )
  }

  return (
    <>
      <Stack.Screen options={{ title: "Sign in", headerShown: false }} />
      <View className="flex-1 items-center justify-center p-6">
        <View className="w-full max-w-sm">
          <LoginForm />
        </View>
      </View>
    </>
  )
}
