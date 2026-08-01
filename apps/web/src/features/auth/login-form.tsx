import { OAuthSignInButtons } from "@/features/auth/oauth-sign-in-buttons"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { useAuth } from "@/lib/auth"
import { cn } from "@/lib/utils"

export function LoginForm({ className, ...props }: React.ComponentProps<"div">) {
  const { refresh } = useAuth()

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader>
          <CardTitle>Login to your account</CardTitle>
        </CardHeader>
        <CardContent>
          <OAuthSignInButtons onSuccess={refresh} />
        </CardContent>
      </Card>
    </div>
  )
}
