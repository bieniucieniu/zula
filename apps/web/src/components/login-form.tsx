import { revalidateLogic } from "@tanstack/react-form"
import {
  getGoogleLoginUrl,
  useAuthenticate,
  useCreateChallenge,
} from "@zula/api/endpoints"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp"
import { useInvalidateSession } from "@/lib/auth"
import { useAppForm } from "@/lib/form"

type LoginFormValues = {
  email: string
  code: string
  challengeId: string
  devCode: string
}

export function LoginForm({ className, ...props }: React.ComponentProps<"div">) {
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

      await authenticate.mutateAsync({
        data: {
          provider: "email_otp",
          challengeId,
          code,
        },
      })

      await invalidateSession()
    },
  })

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader>
          <CardTitle>Login to your account</CardTitle>
          <form.Subscribe selector={(state) => state.values.challengeId}>
            {(challengeId) => (
              <CardDescription>
                {challengeId
                  ? "Enter the code we emailed you."
                  : "We will email you a one-time code."}
              </CardDescription>
            )}
          </form.Subscribe>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              e.stopPropagation()
              void form.handleSubmit()
            }}
          >
            <FieldGroup>
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
                  <Field>
                    <FieldLabel htmlFor={field.name}>Email</FieldLabel>
                    <Input
                      id={field.name}
                      name={field.name}
                      type="email"
                      placeholder="m@example.com"
                      required
                      autoComplete="email"
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(e) => {
                        field.handleChange(e.target.value)
                        form.setFieldValue("challengeId", "")
                        form.setFieldValue("devCode", "")
                        form.setFieldValue("code", "")
                      }}
                    />
                    {field.state.meta.errors.length > 0 ? (
                      <p className="text-xs text-destructive" role="alert">
                        {field.state.meta.errors.join(", ")}
                      </p>
                    ) : null}
                  </Field>
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
                        <Field>
                          <FieldLabel htmlFor={field.name}>Verification code</FieldLabel>
                          <form.Subscribe selector={(state) => state.isSubmitting}>
                            {(isSubmitting) => (
                              <InputOTP
                                id={field.name}
                                maxLength={6}
                                value={field.state.value}
                                onChange={(value) => field.handleChange(value)}
                                disabled={isSubmitting}
                              >
                                <InputOTPGroup>
                                  <InputOTPSlot index={0} />
                                  <InputOTPSlot index={1} />
                                  <InputOTPSlot index={2} />
                                  <InputOTPSlot index={3} />
                                  <InputOTPSlot index={4} />
                                  <InputOTPSlot index={5} />
                                </InputOTPGroup>
                              </InputOTP>
                            )}
                          </form.Subscribe>
                          <form.Subscribe selector={(state) => state.values.devCode}>
                            {(devCode) =>
                              devCode ? (
                                <FieldDescription>Dev code: {devCode}</FieldDescription>
                              ) : null
                            }
                          </form.Subscribe>
                          {field.state.meta.errors.length > 0 ? (
                            <p className="text-xs text-destructive" role="alert">
                              {field.state.meta.errors.join(", ")}
                            </p>
                          ) : null}
                        </Field>
                      )}
                    </form.Field>
                  ) : null
                }
              </form.Subscribe>

              <Field>
                <form.Subscribe
                  selector={(state) =>
                    [state.isSubmitting, state.values.challengeId, state.values.code] as const
                  }
                >
                  {([isSubmitting, challengeId, code]) => (
                    <>
                      <Button
                        type="submit"
                        disabled={
                          isSubmitting ||
                          createChallenge.isPending ||
                          !challengeId ||
                          code.length < 6
                        }
                      >
                        {isSubmitting || authenticate.isPending
                          ? "Verifying…"
                          : createChallenge.isPending
                            ? "Sending code…"
                            : "Verify and sign in"}
                      </Button>
                      <Button
                        variant="outline"
                        type="button"
                        disabled={isSubmitting}
                        onClick={() => {
                          window.location.assign(getGoogleLoginUrl())
                        }}
                      >
                        Continue with Google
                      </Button>
                    </>
                  )}
                </form.Subscribe>
              </Field>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
