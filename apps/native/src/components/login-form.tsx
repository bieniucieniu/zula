import { useState } from "react"
import { View } from "react-native"
import { OAuthSignInButtons } from "@/components/oauth-sign-in-buttons"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Text } from "@/components/ui/text"
import { authenticateNativeDevBypass } from "@/lib/api"
import { useAuth } from "@/lib/auth"
import { getDevAuthConfig } from "@/lib/dev-auth"
import { cn } from "@/lib/utils"

type LoginFormProps = React.ComponentProps<typeof View>

export function LoginForm({ className, ...props }: LoginFormProps) {
  const { refresh } = useAuth()
  const devAuth = getDevAuthConfig()
  const [devPending, setDevPending] = useState(false)

  return (
    <View className={cn("flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader>
          <CardTitle>Login to your account</CardTitle>
        </CardHeader>
        <CardContent className="flex-col gap-2">
          <OAuthSignInButtons disabled={devPending} onSuccess={refresh} />
          {devAuth ? (
            <Button
              variant="secondary"
              className="w-full"
              disabled={devPending}
              onPress={() => {
                setDevPending(true)
                void authenticateNativeDevBypass(devAuth.secret)
                  .then(() => refresh())
                  .catch((error: unknown) => {
                    console.error("Dev sign-in failed", error)
                  })
                  .finally(() => {
                    setDevPending(false)
                  })
              }}
            >
              <Text>{devPending ? "Signing in…" : `Dev sign in (${devAuth.email})`}</Text>
            </Button>
          ) : null}
        </CardContent>
      </Card>
    </View>
  )
}
