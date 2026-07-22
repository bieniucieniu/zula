import { revalidateLogic } from "@tanstack/react-form"
import { useAuthenticate, useCreateChallenge } from "@zula/api/endpoints"
import { View } from "react-native"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Text } from "@/components/ui/text"
import { useInvalidateSession } from "@/lib/auth"
import { useAppForm } from "@/lib/form"
import { saveTokens } from "@/lib/token-store"

type LoginFormValues = {
  email: string
  code: string
  challengeId: string
  devCode: string
}

export function LoginForm() {
  const invalidateSession = useInvalidateSession()
  const createChallenge = useCreateChallenge()
  const authenticate = useAuthenticate()

  const form = useAppForm({
    defaultValues: {
      email: "",
      code: "",
      challengeId: "",
      devCode: "",
    } satisfies LoginFormValues,
    validationLogic: revalidateLogic({
      mode: "blur",
      modeAfterSubmission: "blur",
    }),
    onSubmit: async ({ value }) => {
      const email = value.email.trim()
      const code = value.code.trim()
      const challengeId = value.challengeId
      if (!email || !challengeId || code.length < 6) return

      const { data: tokens } = await authenticate.mutateAsync({
        data: {
          provider: "email_otp",
          challengeId,
          code,
        },
      })

      await saveTokens(tokens.accessToken, tokens.refreshToken)
      await invalidateSession()
    },
  })

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>Login to your account</CardTitle>
        <form.Subscribe selector={(state) => state.values.challengeId}>
          {(challengeId) => (
            <CardDescription>
              {challengeId ? "Enter the code we emailed you." : "We will email you a one-time code."}
            </CardDescription>
          )}
        </form.Subscribe>
      </CardHeader>
      <CardContent className="gap-4">
        <form.Field
          name="email"
          validators={{
            onDynamicAsyncDebounceMs: 400,
            onDynamicAsync: async ({ value }) => {
              const email = value.trim()
              if (!email) return "Email required"

              try {
                const { data } = await createChallenge.mutateAsync({
                  data: {
                    channel: "email",
                    target: email,
                    purpose: "login",
                  },
                })
                form.setFieldValue("challengeId", data.challengeId)
                form.setFieldValue("devCode", data.token ?? "")
                if (data.token) form.setFieldValue("code", data.token)
                return undefined
              } catch (err) {
                form.setFieldValue("challengeId", "")
                form.setFieldValue("devCode", "")
                return err instanceof Error ? err.message : "Could not send code"
              }
            },
          }}
        >
          {(field) => (
            <View className="gap-2">
              <Text className="text-sm font-medium">Email</Text>
              <Input
                autoCapitalize="none"
                autoComplete="email"
                keyboardType="email-address"
                placeholder="m@example.com"
                value={field.state.value}
                onBlur={field.handleBlur}
                onChangeText={(text) => {
                  field.handleChange(text)
                  form.setFieldValue("challengeId", "")
                  form.setFieldValue("devCode", "")
                  form.setFieldValue("code", "")
                }}
              />
              {field.state.meta.errors.length > 0 ? (
                <Text className="text-destructive text-xs">{field.state.meta.errors.join(", ")}</Text>
              ) : null}
            </View>
          )}
        </form.Field>

        <form.Subscribe selector={(state) => state.values.challengeId}>
          {(challengeId) =>
            challengeId ? (
              <form.Field
                name="code"
                validators={{
                  onDynamic: ({ value }) =>
                    value.trim().length < 6 ? "Enter the 6-digit code" : undefined,
                }}
              >
                {(field) => (
                  <View className="gap-2">
                    <Text className="text-sm font-medium">Verification code</Text>
                    <form.Subscribe selector={(state) => state.isSubmitting}>
                      {(isSubmitting) => (
                        <Input
                          editable={!isSubmitting}
                          keyboardType="number-pad"
                          maxLength={6}
                          placeholder="000000"
                          value={field.state.value}
                          onChangeText={(text) => field.handleChange(text)}
                        />
                      )}
                    </form.Subscribe>
                    <form.Subscribe selector={(state) => state.values.devCode}>
                      {(devCode) =>
                        devCode ? (
                          <Text className="text-muted-foreground text-xs">Dev code: {devCode}</Text>
                        ) : null
                      }
                    </form.Subscribe>
                    {field.state.meta.errors.length > 0 ? (
                      <Text className="text-destructive text-xs">
                        {field.state.meta.errors.join(", ")}
                      </Text>
                    ) : null}
                  </View>
                )}
              </form.Field>
            ) : null
          }
        </form.Subscribe>

        <form.Subscribe
          selector={(state) =>
            [state.isSubmitting, state.values.challengeId, state.values.code] as const
          }
        >
          {([isSubmitting, challengeId, code]) => (
            <Button
              disabled={
                isSubmitting || createChallenge.isPending || !challengeId || code.length < 6
              }
              onPress={() => void form.handleSubmit()}
            >
              <Text>
                {isSubmitting || authenticate.isPending
                  ? "Verifying…"
                  : createChallenge.isPending
                    ? "Sending code…"
                    : "Verify and sign in"}
              </Text>
            </Button>
          )}
        </form.Subscribe>
      </CardContent>
    </Card>
  )
}
