import { Redirect, Stack } from "expo-router"
import { ActivityIndicator, View } from "react-native"
import { Button } from "@/components/ui/button"
import { Text } from "@/components/ui/text"
import { useAuth } from "@/lib/auth"
import { useGoogleSignIn } from "@/lib/google-auth"

export default function LoginScreen() {
  const { session, ready } = useAuth()
  const google = useGoogleSignIn()

  if (!ready) {
    return (
      <View className="flex-1 items-center justify-center">
        <ActivityIndicator />
      </View>
    )
  }

  if (session) {
    return <Redirect href="/" />
  }

  return (
    <>
      <Stack.Screen options={{ title: "Sign in" }} />
      <View className="flex-1 items-center justify-center gap-4 p-6">
        <Text className="text-center text-lg font-medium">Sign in to Zula</Text>
        {google.loading ? (
          <ActivityIndicator />
        ) : google.ready ? (
          <Button onPress={() => void google.promptAsync()}>
            <Text>Continue with Google</Text>
          </Button>
        ) : (
          <Text className="text-center text-sm text-muted-foreground">
            Google sign-in is not configured for this build.
          </Text>
        )}
      </View>
    </>
  )
}
