import { useListProviders } from "@zula/api/endpoints"
import { getProviderDefinition } from "@zula/oauth"
import { Redirect, Stack } from "expo-router"
import { useState } from "react"
import { ActivityIndicator, View } from "react-native"
import { Button } from "@/components/ui/button"
import { Text } from "@/components/ui/text"
import { useAuth } from "@/lib/auth"
import { isDevAuthEnabled } from "@/lib/dev-auth"
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
      <Text>{oauth.pending ? "Signing in…" : `Continue with ${oauth.label}`}</Text>
    </Button>
  )
}

export default function LoginScreen() {
  const { session, ready, signInWithDevBypass } = useAuth()
  const [devPending, setDevPending] = useState(false)
  const providersQuery = useListProviders({
    query: {
      retry: 0,
    },
  })
  const providers = providersQuery.data?.data.providers ?? []

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
        {isDevAuthEnabled() ? (
          <Button
            disabled={devPending}
            variant="outline"
            onPress={() => {
              setDevPending(true)
              void signInWithDevBypass()
                .catch((error: unknown) => {
                  console.error("Dev sign-in failed", error)
                })
                .finally(() => {
                  setDevPending(false)
                })
            }}
          >
            <Text>{devPending ? "Signing in…" : "Dev sign in"}</Text>
          </Button>
        ) : null}
      </View>
    </>
  )
}
