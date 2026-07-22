import { Redirect, Stack } from "expo-router"
import { ActivityIndicator, View } from "react-native"
import { getProviderDefinition } from "@zula/oauth"
import { useOAuthProviders } from "@zula/oauth/react"
import { Button } from "@/components/ui/button"
import { Text } from "@/components/ui/text"
import { useAuth } from "@/lib/auth"
import { useProviderOAuthSignIn } from "@/lib/oauth-sign-in"

function ProviderSignInButton({ providerId }: { providerId: string }) {
  const oauth = useProviderOAuthSignIn(providerId)

  if (oauth.loading) {
    return <ActivityIndicator />
  }

  if (!oauth.ready) {
    return null
  }

  return (
    <Button disabled={oauth.pending} onPress={() => void oauth.signIn()}>
      <Text>
        {oauth.pending ? "Signing in…" : `Continue with ${oauth.label}`}
      </Text>
    </Button>
  )
}

export default function LoginScreen() {
  const { session, ready } = useAuth()
  const providersQuery = useOAuthProviders()
  const providers = providersQuery.data ?? []

  if (!ready || providersQuery.isLoading) {
    return (
      <View className="flex-1 items-center justify-center">
        <ActivityIndicator />
      </View>
    )
  }

  if (session) {
    return <Redirect href="/" />
  }

  const configuredProviders = providers.filter((provider) => getProviderDefinition(provider.id))

  return (
    <>
      <Stack.Screen options={{ title: "Sign in" }} />
      <View className="flex-1 items-center justify-center gap-4 p-6">
        <Text className="text-center text-lg font-medium">Sign in to Zula</Text>
        {configuredProviders.length > 0 ? (
          configuredProviders.map((provider) => (
            <ProviderSignInButton key={provider.id} providerId={provider.id} />
          ))
        ) : (
          <Text className="text-center text-sm text-muted-foreground">
            OAuth sign-in is not configured for this build.
          </Text>
        )}
      </View>
    </>
  )
}
