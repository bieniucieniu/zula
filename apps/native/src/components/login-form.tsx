import { OAuthSignInButtons } from "@/components/oauth-sign-in-buttons"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { useAuth } from "@/lib/auth"
import { cn } from "@/lib/utils"
import { View } from "react-native"

type LoginFormProps = React.ComponentProps<typeof View>

export function LoginForm({ className, ...props }: LoginFormProps) {
  const { refresh } = useAuth()

  return (
    <View className={cn("flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader>
          <CardTitle>Login to your account</CardTitle>
        </CardHeader>
        <CardContent className="flex-col gap-2">
          <OAuthSignInButtons onSuccess={refresh} />
        </CardContent>
      </Card>
    </View>
  )
}
