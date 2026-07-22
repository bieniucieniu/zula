import { Redirect, Stack } from "expo-router"
import { View } from "react-native"
import { LoginForm } from "@/components/login-form"
import { Text } from "@/components/ui/text"
import { useAuth } from "@/lib/auth"

export default function LoginScreen() {
  const { session, ready } = useAuth()

  if (!ready) {
    return (
      <View className="flex-1 items-center justify-center p-6">
        <Text className="text-muted-foreground text-sm">Loading…</Text>
      </View>
    )
  }

  if (session) {
    return <Redirect href="/" />
  }

  return (
    <>
      <Stack.Screen options={{ title: "Login", headerTransparent: true }} />
      <View className="flex-1 items-center justify-center p-6">
        <LoginForm />
      </View>
    </>
  )
}
